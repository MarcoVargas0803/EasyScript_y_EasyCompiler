// Archivo: OptimizacionBucles.java
//
// F4. Optimizacion de bucles.
//
// Trabaja sobre los bucles naturales del grafo de flujo, del mas interno al
// mas externo. Cada bucle tiene su cabecera (la etiqueta a la que vuelve) y se
// le da un bloque de iniciacion: codigo que se ejecuta una vez, justo antes de
// entrar. Los saltos que llegaban a la cabecera desde fuera se redirigen al
// inicio de ese bloque; los de dentro siguen yendo a la cabecera.
//
// 1. Codigo invariante. Un calculo cuyo resultado es un temporal se saca al
//    bloque de iniciacion si cada operando es una constante, un nombre que no
//    se asigna dentro del bucle (A[v] = 0), o un temporal que ya se saco. El
//    temporal no debe usarse fuera del bucle. No se saca una division (o un
//    modulo) cuyo divisor no sea una constante distinta de cero: si el bucle
//    no da ninguna vuelta, el programa original no la ejecuta y no debe fallar.
//
// 2. Reduccion de fuerza. Una variable de induccion basica I tiene una sola
//    asignacion en el bucle, de la forma I = I + c (o I - c). Un temporal
//    derivado vale a*I + b, con a constante y b invariante (por ejemplo
//    t = I * 4, o t = (F*3 + C) * 4 en una matriz). Cada multiplicacion
//    derivada se sustituye por una variable s:
//      - inicializacion: s = a*I + b, en el bloque de iniciacion
//      - incremento:     s = s + a*c, justo despues de I = I + c
//      - cuerpo:         t = s   (la multiplicacion desaparece)
//    Como I solo cambia en su incremento y s se actualiza justo despues, s vale
//    a*I + b en todo el resto del bucle.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.Cuadruplo;
import backend.SimuladorCuadruplos;

final class OptimizacionBucles implements Fase {

    public String clave() {
        return "F4";
    }

    public String nombre() {
        return "Optimizacion de bucles";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean algo = false;
        while (unaVuelta(programa, bitacora)) {
            algo = true;
            Cuadruplos.renumerar(programa.codigo);
        }
        return algo;
    }

    /** Aplica una transformacion a un bucle y vuelve (el grafo queda viejo). */
    private boolean unaVuelta(ProgramaIntermedio programa, Bitacora bitacora) {
        GrafoFlujo grafo = new GrafoFlujo(programa.codigo, Collections.<String>emptySet());
        for (GrafoFlujo.Bucle bucle : grafo.bucles) {
            if (sacarInvariantes(programa, bitacora, grafo, bucle)) {
                return true;
            }
            if (reducirFuerza(programa, bitacora, grafo, bucle)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 1. Codigo invariante
    // ------------------------------------------------------------------

    private boolean sacarInvariantes(ProgramaIntermedio programa, Bitacora bitacora,
                                     GrafoFlujo grafo, GrafoFlujo.Bucle bucle) {
        List<Cuadruplo> codigo = programa.codigo;
        List<Integer> dentro = instrucciones(grafo, bucle);
        Set<String> asignadas = asignadasEn(codigo, dentro);
        Map<String, Integer> asignaciones = EliminacionRedundancias.contarAsignaciones(codigo);

        List<Cuadruplo> invariantes = new ArrayList<Cuadruplo>();
        Set<String> temporalesInvariantes = new HashSet<String>();
        boolean progreso = true;
        while (progreso) {
            progreso = false;
            for (Integer i : dentro) {
                Cuadruplo c = codigo.get(i.intValue());
                if (invariantes.contains(c) || !Cuadruplos.esCalculo(c) || !Cuadruplos.esTemporal(c.resultado)
                        || EliminacionRedundancias.cuenta(asignaciones, c.resultado) != 1
                        || seUsaFuera(codigo, c.resultado, dentro) || Cuadruplos.puedeFallar(c)) {
                    continue;
                }
                boolean todos = true;
                for (String u : Cuadruplos.usos(c)) {
                    if (asignadas.contains(u) && !temporalesInvariantes.contains(u)) {
                        todos = false;
                        break;
                    }
                }
                if (todos) {
                    invariantes.add(c);
                    temporalesInvariantes.add(c.resultado);
                    progreso = true;
                }
            }
        }
        if (invariantes.isEmpty()) {
            return false;
        }
        Iniciacion plan = planificar(grafo, bucle);
        if (plan == null) {
            return false;
        }
        String cabecera = plan.etiqueta.resultado;
        for (Cuadruplo c : invariantes) {
            codigo.remove(indiceDe(codigo, c));
        }
        insertar(programa, bitacora, plan, invariantes);
        for (Cuadruplo c : invariantes) {
            bitacora.registrar(clave(), "invariante del bucle " + cabecera + ", sale al bloque de iniciacion",
                c.comentario, c.comentario + "  (antes de " + cabecera + ")");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 2. Reduccion de fuerza
    // ------------------------------------------------------------------

    /** a*I + b, con b = mult*nombre (o sin b). */
    private static final class Familia {
        final String induccion;
        final long a;
        final String b;
        final long multB;

        Familia(String induccion, long a, String b, long multB) {
            this.induccion = induccion;
            this.a = a;
            this.b = b;
            this.multB = multB;
        }

        String clave() {
            return induccion + "*" + a + "+" + b + "*" + multB;
        }
    }

    private boolean reducirFuerza(ProgramaIntermedio programa, Bitacora bitacora,
                                  GrafoFlujo grafo, GrafoFlujo.Bucle bucle) {
        List<Cuadruplo> codigo = programa.codigo;
        List<Integer> dentro = instrucciones(grafo, bucle);
        Set<String> asignadas = asignadasEn(codigo, dentro);
        Map<String, Integer> asignaciones = EliminacionRedundancias.contarAsignaciones(codigo);

        // Variables de induccion basicas: una sola asignacion en el bucle, I = I +- c.
        Map<String, Cuadruplo> incremento = new HashMap<String, Cuadruplo>();
        Map<String, Integer> asignacionesDentro = new HashMap<String, Integer>();
        for (Integer i : dentro) {
            Cuadruplo c = codigo.get(i.intValue());
            String d = Cuadruplos.definicion(c);
            if (d != null) {
                asignacionesDentro.put(d, Integer.valueOf(EliminacionRedundancias.cuenta(asignacionesDentro, d) + 1));
            }
        }
        for (Integer i : dentro) {
            Cuadruplo c = codigo.get(i.intValue());
            if (("+".equals(c.operador) || "-".equals(c.operador)) && c.resultado.equals(c.arg1)
                    && literal(c.arg2) instanceof Long && "ENT".equals(programa.tipoDe(c.resultado))
                    && !programa.arreglos.contains(c.resultado)
                    && EliminacionRedundancias.cuenta(asignacionesDentro, c.resultado) == 1) {
                incremento.put(c.resultado, c);
            }
        }
        if (incremento.isEmpty()) {
            return false;
        }

        // Temporales derivados, en orden: cada uno puede apoyarse en los anteriores.
        Map<String, Familia> familia = new HashMap<String, Familia>();
        List<Cuadruplo> multiplicaciones = new ArrayList<Cuadruplo>();
        boolean progreso = true;
        while (progreso) {
            progreso = false;
            for (Integer i : dentro) {
                Cuadruplo c = codigo.get(i.intValue());
                String t = c.resultado;
                if (!Cuadruplos.esCalculo(c) || !Cuadruplos.esTemporal(t) || familia.containsKey(t)
                        || !"ENT".equals(programa.tipoDe(t))
                        || EliminacionRedundancias.cuenta(asignaciones, t) != 1) {
                    continue;
                }
                Familia f = derivar(c, incremento, familia, asignadas);
                if (f != null) {
                    familia.put(t, f);
                    if ("*".equals(c.operador)) {
                        multiplicaciones.add(c);
                    }
                    progreso = true;
                }
            }
        }
        if (multiplicaciones.isEmpty()) {
            return false;
        }
        Iniciacion plan = planificar(grafo, bucle);
        if (plan == null) {
            return false;
        }

        // Una variable s por familia distinta.
        Map<String, String> variableDe = new LinkedHashMap<String, String>();
        List<Cuadruplo> iniciacion = new ArrayList<Cuadruplo>();
        Map<Cuadruplo, List<Cuadruplo>> trasIncremento = new LinkedHashMap<Cuadruplo, List<Cuadruplo>>();
        for (Cuadruplo c : multiplicaciones) {
            Familia f = familia.get(c.resultado);
            String s = variableDe.get(f.clave());
            if (s == null) {
                s = programa.nuevoTemporal("ENT");
                variableDe.put(f.clave(), s);
                iniciacion.addAll(inicializar(programa, s, f, c));
                Cuadruplo inc = incremento.get(f.induccion);
                long paso = literalEntero(inc.arg2) * f.a;
                Cuadruplo actualizar = ProgramaIntermedio.nuevo(inc.operador, s, String.valueOf(paso), s);
                Cuadruplos.heredarOrigen(actualizar, inc);
                List<Cuadruplo> l = trasIncremento.get(inc);
                if (l == null) {
                    l = new ArrayList<Cuadruplo>();
                    trasIncremento.put(inc, l);
                }
                l.add(actualizar);
            }
            String antes = c.comentario;
            c.operador = "=";
            c.arg1 = s;
            c.arg2 = "-";
            ProgramaIntermedio.reescribir(c);
            bitacora.registrar(clave(), "reduccion de fuerza (" + describir(f) + "): cuerpo", antes, c.comentario);
        }
        for (Map.Entry<Cuadruplo, List<Cuadruplo>> e : trasIncremento.entrySet()) {
            int k = indiceDe(codigo, e.getKey());
            codigo.addAll(k + 1, e.getValue());
            for (Cuadruplo a : e.getValue()) {
                bitacora.registrar(clave(), "reduccion de fuerza: incremento, tras " + e.getKey().comentario,
                    "(nueva)", a.comentario);
            }
        }
        insertar(programa, bitacora, plan, iniciacion);
        for (Cuadruplo a : iniciacion) {
            bitacora.registrar(clave(), "reduccion de fuerza: inicializacion antes de " + plan.etiqueta.resultado,
                "(nueva)", a.comentario);
        }
        return true;
    }

    /** La familia del temporal que calcula c, o null si no es derivado. */
    private static Familia derivar(Cuadruplo c, Map<String, Cuadruplo> incremento,
                                   Map<String, Familia> familia, Set<String> asignadas) {
        String x = c.arg1;
        String y = c.arg2;
        if ("*".equals(c.operador)) {
            Familia base = base(x, incremento, familia);
            Object k = literal(y);
            if (base == null) {
                base = base(y, incremento, familia);
                k = literal(x);
            }
            if (base == null || !(k instanceof Long)) {
                return null;
            }
            long m = ((Long) k).longValue();
            return new Familia(base.induccion, base.a * m, base.b, base.multB * m);
        }
        if ("+".equals(c.operador)) {
            Familia base = base(x, incremento, familia);
            String otro = y;
            if (base == null) {
                base = base(y, incremento, familia);
                otro = x;
            }
            if (base == null || base.b != null || Cuadruplos.vacia(otro)
                    || (Cuadruplos.esNombre(otro) && asignadas.contains(otro))) {
                return null;
            }
            return new Familia(base.induccion, base.a, otro, 1);
        }
        return null;
    }

    /** La familia de un operando: la variable de induccion misma (1*I) o un derivado. */
    private static Familia base(String operando, Map<String, Cuadruplo> incremento, Map<String, Familia> familia) {
        if (incremento.containsKey(operando)) {
            return new Familia(operando, 1, null, 0);
        }
        return familia.get(operando);
    }

    /** s = a*I [+ mult*b], escrito en cuadruplos. */
    private static List<Cuadruplo> inicializar(ProgramaIntermedio programa, String s, Familia f, Cuadruplo origen) {
        List<Cuadruplo> l = new ArrayList<Cuadruplo>();
        l.add(ProgramaIntermedio.nuevo("*", f.induccion, String.valueOf(f.a), s));
        if (f.b != null) {
            String sumando = f.b;
            if (f.multB != 1) {
                sumando = programa.nuevoTemporal("ENT");
                l.add(ProgramaIntermedio.nuevo("*", f.b, String.valueOf(f.multB), sumando));
            }
            l.add(ProgramaIntermedio.nuevo("+", s, sumando, s));
        }
        for (Cuadruplo c : l) {
            Cuadruplos.heredarOrigen(c, origen);
        }
        return l;
    }

    private static String describir(Familia f) {
        String s = f.a + "*" + f.induccion;
        if (f.b != null) {
            s += " + " + (f.multB == 1 ? "" : f.multB + "*") + f.b;
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Bloque de iniciacion
    // ------------------------------------------------------------------

    /** Donde va el bloque de iniciacion: la etiqueta de cabecera y los saltos de fuera. */
    private static final class Iniciacion {
        final Cuadruplo etiqueta;
        final List<Cuadruplo> saltosDeFuera;

        Iniciacion(Cuadruplo etiqueta, List<Cuadruplo> saltosDeFuera) {
            this.etiqueta = etiqueta;
            this.saltosDeFuera = saltosDeFuera;
        }
    }

    /**
     * Lee del grafo (antes de tocar el codigo) donde ira el bloque de
     * iniciacion. null si el bucle no tiene un sitio seguro para el.
     */
    private static Iniciacion planificar(GrafoFlujo grafo, GrafoFlujo.Bucle bucle) {
        List<Cuadruplo> codigo = grafo.codigo;
        GrafoFlujo.Bloque cabecera = grafo.bloques.get(bucle.cabecera);
        Cuadruplo etiqueta = codigo.get(cabecera.inicio);
        if (!Cuadruplos.esEtiqueta(etiqueta)) {
            return null;
        }
        // Lo que esta justo antes de la cabecera debe ser de fuera del bucle,
        // o el codigo insertado se ejecutaria en cada vuelta.
        if (cabecera.inicio > 0) {
            GrafoFlujo.Bloque previo = grafo.bloqueDeInstruccion(cabecera.inicio - 1);
            if (bucle.contiene(previo.numero) && !"ir_a".equals(codigo.get(previo.fin).operador)) {
                return null;
            }
        }
        List<Cuadruplo> saltosDeFuera = new ArrayList<Cuadruplo>();
        for (GrafoFlujo.Bloque b : grafo.bloques) {
            Cuadruplo ultimo = codigo.get(b.fin);
            if (!bucle.contiene(b.numero) && Cuadruplos.esSalto(ultimo) && etiqueta.resultado.equals(ultimo.resultado)) {
                saltosDeFuera.add(ultimo);
            }
        }
        return new Iniciacion(etiqueta, saltosDeFuera);
    }

    /**
     * Pone 'nuevas' justo antes de la etiqueta de cabecera. Si algun salto de
     * fuera llegaba a la cabecera, se crea una etiqueta al principio del codigo
     * insertado y esos saltos se redirigen a ella; los de dentro del bucle
     * siguen yendo a la cabecera.
     */
    private void insertar(ProgramaIntermedio programa, Bitacora bitacora, Iniciacion plan, List<Cuadruplo> nuevas) {
        List<Cuadruplo> codigo = programa.codigo;
        List<Cuadruplo> bloque = new ArrayList<Cuadruplo>();
        if (!plan.saltosDeFuera.isEmpty()) {
            Cuadruplo inicio = ProgramaIntermedio.nuevo("etiqueta", "-", "-", programa.nuevaEtiqueta());
            Cuadruplos.heredarOrigen(inicio, plan.etiqueta);
            bloque.add(inicio);
            for (Cuadruplo s : plan.saltosDeFuera) {
                String antes = s.comentario;
                s.resultado = inicio.resultado;
                ProgramaIntermedio.reescribir(s);
                bitacora.registrar(clave(), "el salto de fuera entra por el bloque de iniciacion", antes, s.comentario);
            }
        }
        bloque.addAll(nuevas);
        codigo.addAll(indiceDe(codigo, plan.etiqueta), bloque);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static List<Integer> instrucciones(GrafoFlujo grafo, GrafoFlujo.Bucle bucle) {
        List<Integer> l = new ArrayList<Integer>();
        for (Integer b : bucle.bloques) {
            GrafoFlujo.Bloque bloque = grafo.bloques.get(b.intValue());
            for (int i = bloque.inicio; i <= bloque.fin; i++) {
                l.add(Integer.valueOf(i));
            }
        }
        Collections.sort(l);
        return l;
    }

    /** Nombres asignados dentro del bucle, incluidos los arreglos escritos (A[v] = 1). */
    private static Set<String> asignadasEn(List<Cuadruplo> codigo, List<Integer> dentro) {
        Set<String> s = new HashSet<String>();
        for (Integer i : dentro) {
            Cuadruplo c = codigo.get(i.intValue());
            String d = Cuadruplos.definicion(c);
            if (d != null) {
                s.add(d);
            }
            String a = Cuadruplos.arregloEscrito(c);
            if (a != null) {
                s.add(a);
            }
        }
        return s;
    }

    private static boolean seUsaFuera(List<Cuadruplo> codigo, String nombre, List<Integer> dentro) {
        Set<Integer> conjunto = new HashSet<Integer>(dentro);
        for (int i = 0; i < codigo.size(); i++) {
            if (!conjunto.contains(Integer.valueOf(i)) && Cuadruplos.usos(codigo.get(i)).contains(nombre)) {
                return true;
            }
        }
        return false;
    }

    /** Posicion de un cuadruplo por identidad (no por igualdad de campos). */
    private static int indiceDe(List<Cuadruplo> codigo, Cuadruplo c) {
        for (int i = 0; i < codigo.size(); i++) {
            if (codigo.get(i) == c) {
                return i;
            }
        }
        throw new IllegalStateException("cuadruplo perdido: " + c);
    }

    private static Object literal(String casilla) {
        try {
            return SimuladorCuadruplos.valorLiteral(casilla);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long literalEntero(String casilla) {
        return ((Long) literal(casilla)).longValue();
    }
}
