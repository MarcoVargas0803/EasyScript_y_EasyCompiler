// Archivo: ReordenacionOperaciones.java
//
// F3. Reordenacion de operaciones.
//
// 1. Orden canonico de operandos. En los operadores conmutativos (+ * == !=
//    && ||) los operandos se escriben siempre en el mismo orden:
//      1) temporales (terminos que no son variables ni constantes)
//      2) arreglos, en orden alfabetico
//      3) variables, en orden alfabetico
//      4) constantes
//    Asi A * B y B * A quedan escritos igual y F2 los reconoce como la misma
//    operacion. Los relacionales < > <= >= tambien se ordenan, invirtiendo el
//    operador (A > B es B < A). La resta, la division y la concatenacion no
//    son conmutativas y no se tocan.
//
// 2. Identidades algebraicas y reduccion de fuerza local:
//      x + 0, x - 0, x * 1, x / 1  ->  x         x * 0  ->  0   (solo ENT)
//      x * 2  ->  x + x                         x ** 2  ->  x * x
//    En DEC solo se usan las que son exactas en coma flotante: x * 1.0,
//    x / 1.0, x * 2.0 y x ** 2.0. (x + 0.0 no lo es: -0.0 + 0.0 da 0.0.)
//
// 3. Reasociacion de constantes enteras: (x + 1) + 2 -> x + 3, y lo mismo con
//    restas y productos. Solo en ENT: en DEC la suma no es asociativa.
//
// 4. Orden de evaluacion por el numero minimo de variables auxiliares: cada
//    hoja vale 0; un nodo cuyos hijos valen todos j vale j+1; si no, el maximo
//    de sus hijos. Si el operando derecho de una operacion necesita mas
//    auxiliares que el izquierdo, se calcula primero: mientras se calcula la
//    rama exigente no hay que guardar el resultado de la otra.
//
// 5. Propagacion de copias: un temporal "t = x" se sustituye por x en sus usos
//    del mismo bloque si x no cambia antes, y la copia desaparece.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import backend.Cuadruplo;
import backend.SimuladorCuadruplos;

final class ReordenacionOperaciones implements Fase {

    public String clave() {
        return "F3";
    }

    public String nombre() {
        return "Reordenacion de operaciones";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean algo = false;
        boolean cambio = true;
        while (cambio) {
            cambio = ordenCanonico(programa, bitacora);
            cambio |= identidades(programa, bitacora);
            cambio |= reasociar(programa, bitacora);
            cambio |= copias(programa, bitacora);
            cambio |= ordenDeEvaluacion(programa, bitacora);
            algo |= cambio;
            Cuadruplos.renumerar(programa.codigo);
        }
        return algo;
    }

    // ------------------------------------------------------------------
    // 1. Orden canonico
    // ------------------------------------------------------------------

    private boolean ordenCanonico(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean cambio = false;
        for (Cuadruplo c : programa.codigo) {
            if (Cuadruplos.vacia(c.arg2) || Cuadruplos.vacia(c.arg1)) {
                continue;
            }
            String op = c.operador;
            boolean salto = op.startsWith("si_");
            String base = salto ? op.substring(3) : op;
            if ("igual".equals(base)) {
                base = "==";
            }
            String espejo = espejo(base);
            if (espejo == null || comparar(programa, c.arg1, c.arg2) <= 0) {
                continue;
            }
            if (salto && !"si_igual".equals(op)) {
                op = "si_" + espejo;
            } else if (!salto) {
                op = espejo;
            }
            String antes = c.comentario;
            String a = c.arg1;
            c.arg1 = c.arg2;
            c.arg2 = a;
            c.operador = op;
            ProgramaIntermedio.reescribir(c);
            bitacora.registrar(clave(), "orden canonico de operandos", antes, c.comentario);
            cambio = true;
        }
        return cambio;
    }

    /** El operador que da lo mismo con los operandos al reves, o null si no hay. */
    private static String espejo(String op) {
        switch (op) {
            case "+": case "*": case "==": case "!=": case "&&": case "||":
                return op;
            case "<":  return ">";
            case ">":  return "<";
            case "<=": return ">=";
            case ">=": return "<=";
            default:   return null;
        }
    }

    /** Orden de los operandos: temporales, arreglos, variables, constantes. */
    private static int comparar(ProgramaIntermedio programa, String a, String b) {
        int ra = rango(programa, a);
        int rb = rango(programa, b);
        if (ra != rb) {
            return Integer.compare(ra, rb);
        }
        if (ra == 1) {
            return Integer.compare(Integer.parseInt(a.substring(1)), Integer.parseInt(b.substring(1)));
        }
        return a.compareTo(b);
    }

    private static int rango(ProgramaIntermedio programa, String s) {
        if (Cuadruplos.esTemporal(s)) return 1;
        if (programa.arreglos.contains(s)) return 2;
        if (Cuadruplos.esNombre(s)) return 3;
        return 4;
    }

    // ------------------------------------------------------------------
    // 2. Identidades algebraicas
    // ------------------------------------------------------------------

    private boolean identidades(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean cambio = false;
        for (Cuadruplo c : programa.codigo) {
            if (!Cuadruplos.esCalculo(c) || Cuadruplos.vacia(c.arg2)) {
                continue;
            }
            Object x1 = literal(c.arg1);
            Object x2 = literal(c.arg2);
            if (x1 != null && x2 != null) {
                continue;   // lo calcula F1
            }
            String op = c.operador;
            String antes = c.comentario;
            String regla = null;
            if ("+".equals(op) && esEntero(x2, 0)) {
                copiar(c, c.arg1);
                regla = "x + 0 = x";
            } else if ("+".equals(op) && esEntero(x1, 0)) {
                copiar(c, c.arg2);
                regla = "0 + x = x";
            } else if ("-".equals(op) && esEntero(x2, 0)) {
                copiar(c, c.arg1);
                regla = "x - 0 = x";
            } else if ("*".equals(op) && (esEntero(x2, 1) || esDecimal(x2, 1.0))) {
                copiar(c, c.arg1);
                regla = "x * 1 = x";
            } else if ("*".equals(op) && (esEntero(x1, 1) || esDecimal(x1, 1.0))) {
                copiar(c, c.arg2);
                regla = "1 * x = x";
            } else if ("*".equals(op) && (esEntero(x1, 0) || esEntero(x2, 0))) {
                copiar(c, "0");
                regla = "x * 0 = 0";
            } else if ("/".equals(op) && (esEntero(x2, 1) || esDecimal(x2, 1.0))) {
                copiar(c, c.arg1);
                regla = "x / 1 = x";
            } else if ("*".equals(op) && (esEntero(x2, 2) || esDecimal(x2, 2.0))) {
                c.operador = "+";
                c.arg2 = c.arg1;
                regla = "x * 2 = x + x (reduccion de fuerza)";
            } else if ("*".equals(op) && (esEntero(x1, 2) || esDecimal(x1, 2.0))) {
                c.operador = "+";
                c.arg1 = c.arg2;
                regla = "2 * x = x + x (reduccion de fuerza)";
            } else if ("**".equals(op) && esDecimal(x2, 2.0)) {
                c.operador = "*";
                c.arg2 = c.arg1;
                regla = "x ** 2 = x * x (reduccion de fuerza)";
            }
            if (regla != null) {
                ProgramaIntermedio.reescribir(c);
                bitacora.registrar(clave(), "identidad " + regla, antes, c.comentario);
                cambio = true;
            }
        }
        return cambio;
    }

    private static void copiar(Cuadruplo c, String valor) {
        c.operador = "=";
        c.arg1 = valor;
        c.arg2 = "-";
    }

    private static Object literal(String casilla) {
        try {
            return SimuladorCuadruplos.valorLiteral(casilla);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean esEntero(Object v, long n) {
        return (v instanceof Long) && ((Long) v).longValue() == n;
    }

    private static boolean esDecimal(Object v, double d) {
        return (v instanceof Double) && ((Double) v).equals(Double.valueOf(d));
    }

    // ------------------------------------------------------------------
    // 3. Reasociacion de constantes enteras
    // ------------------------------------------------------------------

    private boolean reasociar(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        Map<String, Integer> asignaciones = EliminacionRedundancias.contarAsignaciones(codigo);
        Map<String, Integer> usos = EliminacionRedundancias.contarUsos(codigo);
        Map<String, Integer> definidoEn = definiciones(codigo);

        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            boolean aditivo = "+".equals(c.operador) || "-".equals(c.operador);
            if (!aditivo && !"*".equals(c.operador)) {
                continue;
            }
            Object c2 = literal(c.arg2);
            String t = c.arg1;
            if (!(c2 instanceof Long) || !Cuadruplos.esTemporal(t) || !"ENT".equals(programa.tipoDe(t))
                    || EliminacionRedundancias.cuenta(asignaciones, t) != 1
                    || EliminacionRedundancias.cuenta(usos, t) != 1) {
                continue;
            }
            Integer k = definidoEn.get(t);
            if (k == null || k.intValue() >= i || grafo.bloqueDeInstruccion(k.intValue()) != grafo.bloqueDeInstruccion(i)) {
                continue;
            }
            Cuadruplo def = codigo.get(k.intValue());
            Object c1 = literal(def.arg2);
            boolean defAditivo = "+".equals(def.operador) || "-".equals(def.operador);
            if (!(c1 instanceof Long) || !Cuadruplos.esNombre(def.arg1)
                    || (aditivo ? !defAditivo : !"*".equals(def.operador))
                    || seRedefine(codigo, def.arg1, k.intValue() + 1, i)) {
                continue;
            }
            long v1 = ((Long) c1).longValue();
            long v2 = ((Long) c2).longValue();
            long total;
            if (aditivo) {
                long k1 = "+".equals(def.operador) ? v1 : -v1;
                total = "+".equals(c.operador) ? k1 + v2 : k1 - v2;
            } else {
                total = v1 * v2;
            }
            String antes = def.comentario + " ; " + c.comentario;
            c.operador = aditivo ? "+" : "*";
            c.arg1 = def.arg1;
            c.arg2 = String.valueOf(total);
            ProgramaIntermedio.reescribir(c);
            codigo.remove(k.intValue());
            bitacora.registrar(clave(), "reasociacion de constantes enteras", antes, c.comentario);
            return true;   // los indices cambiaron: se vuelve a empezar
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 4. Orden de evaluacion
    // ------------------------------------------------------------------

    private boolean ordenDeEvaluacion(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        Map<String, Integer> asignaciones = EliminacionRedundancias.contarAsignaciones(codigo);
        Map<String, Integer> usos = EliminacionRedundancias.contarUsos(codigo);
        Map<String, Integer> definidoEn = definiciones(codigo);
        Map<String, Integer> etiqueta = MetricasCodigo.etiquetasDeAuxiliares(codigo);

        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            if (!Cuadruplos.esCalculo(c) || !Cuadruplos.esTemporal(c.arg1) || !Cuadruplos.esTemporal(c.arg2)
                    || c.arg1.equals(c.arg2)) {
                continue;
            }
            GrafoFlujo.Bloque bloque = grafo.bloqueDeInstruccion(i);
            TreeSet<Integer> a = subarbol(codigo, c.arg1, bloque, grafo, asignaciones, usos, definidoEn);
            TreeSet<Integer> b = subarbol(codigo, c.arg2, bloque, grafo, asignaciones, usos, definidoEn);
            if (a == null || b == null || !contiguo(a) || !contiguo(b)
                    || a.last().intValue() + 1 != b.first().intValue() || b.last().intValue() + 1 != i) {
                continue;
            }
            int ea = valor(etiqueta, c.arg1);
            int eb = valor(etiqueta, c.arg2);
            if (eb <= ea) {
                continue;
            }
            // La rama derecha (b) es mas exigente: se calcula primero.
            List<Cuadruplo> ramaA = new ArrayList<Cuadruplo>(codigo.subList(a.first().intValue(), a.last().intValue() + 1));
            List<Cuadruplo> ramaB = new ArrayList<Cuadruplo>(codigo.subList(b.first().intValue(), b.last().intValue() + 1));
            int inicio = a.first().intValue();
            for (int k = 0; k < ramaB.size(); k++) {
                codigo.set(inicio + k, ramaB.get(k));
            }
            for (int k = 0; k < ramaA.size(); k++) {
                codigo.set(inicio + ramaB.size() + k, ramaA.get(k));
            }
            bitacora.registrar(clave(), "orden de evaluacion: la rama de " + c.arg2 + " necesita " + eb
                + " auxiliares y la de " + c.arg1 + " " + ea + ", se calcula primero", c.comentario, c.comentario);
            return true;
        }
        return false;
    }

    /**
     * Instrucciones que calculan el temporal t y sus operandos temporales, si
     * todas son calculos del mismo bloque cuyos temporales se usan una sola
     * vez. null si no forman un subarbol que se pueda mover.
     */
    private static TreeSet<Integer> subarbol(List<Cuadruplo> codigo, String t, GrafoFlujo.Bloque bloque,
                                             GrafoFlujo grafo, Map<String, Integer> asignaciones,
                                             Map<String, Integer> usos, Map<String, Integer> definidoEn) {
        Integer d = definidoEn.get(t);
        if (d == null || EliminacionRedundancias.cuenta(asignaciones, t) != 1
                || EliminacionRedundancias.cuenta(usos, t) != 1
                || grafo.bloqueDeInstruccion(d.intValue()) != bloque) {
            return null;
        }
        Cuadruplo c = codigo.get(d.intValue());
        if (!Cuadruplos.esCalculo(c)) {
            return null;
        }
        TreeSet<Integer> nodos = new TreeSet<Integer>();
        nodos.add(d);
        for (String u : Cuadruplos.usos(c)) {
            if (Cuadruplos.esTemporal(u)) {
                TreeSet<Integer> hijo = subarbol(codigo, u, bloque, grafo, asignaciones, usos, definidoEn);
                if (hijo == null) {
                    return null;
                }
                nodos.addAll(hijo);
            }
        }
        return nodos;
    }

    private static boolean contiguo(TreeSet<Integer> nodos) {
        return nodos.last().intValue() - nodos.first().intValue() + 1 == nodos.size();
    }

    private static int valor(Map<String, Integer> etiqueta, String t) {
        Integer e = etiqueta.get(t);
        return (e == null) ? 0 : e.intValue();
    }

    // ------------------------------------------------------------------
    // 5. Propagacion de copias
    // ------------------------------------------------------------------

    private boolean copias(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        Map<String, Integer> asignaciones = EliminacionRedundancias.contarAsignaciones(codigo);

        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            String t = c.resultado;
            if (!"=".equals(c.operador) || !Cuadruplos.esTemporal(t)
                    || EliminacionRedundancias.cuenta(asignaciones, t) != 1 || t.equals(c.arg1)) {
                continue;
            }
            List<Integer> usosDeT = new ArrayList<Integer>();
            boolean valido = true;
            for (int k = 0; k < codigo.size() && valido; k++) {
                if (Cuadruplos.usos(codigo.get(k)).contains(t)) {
                    valido = k > i && grafo.bloqueDeInstruccion(k) == grafo.bloqueDeInstruccion(i);
                    usosDeT.add(Integer.valueOf(k));
                }
            }
            if (!valido) {
                continue;
            }
            int ultimo = usosDeT.isEmpty() ? i : usosDeT.get(usosDeT.size() - 1).intValue();
            if (Cuadruplos.esNombre(c.arg1) && seRedefine(codigo, c.arg1, i + 1, ultimo)) {
                continue;
            }
            Map<String, String> sustitucion = Collections.singletonMap(t, c.arg1);
            for (Integer k : usosDeT) {
                Cuadruplo u = codigo.get(k.intValue());
                String antes = u.comentario;
                EliminacionRedundancias.aplicarSustituciones(u, sustitucion);
                bitacora.registrar(clave(), "propagacion de copia " + t + " = " + c.arg1, antes, u.comentario);
            }
            bitacora.registrar(clave(), "copia propagada", c.comentario, "(eliminada)");
            codigo.remove(i);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Indice de la (unica o ultima) asignacion de cada nombre. */
    private static Map<String, Integer> definiciones(List<Cuadruplo> codigo) {
        Map<String, Integer> d = new HashMap<String, Integer>();
        for (int i = 0; i < codigo.size(); i++) {
            String n = Cuadruplos.definicion(codigo.get(i));
            if (n != null) {
                d.put(n, Integer.valueOf(i));
            }
        }
        return d;
    }

    /** true si 'nombre' se asigna en alguna instruccion de [desde, hasta). */
    private static boolean seRedefine(List<Cuadruplo> codigo, String nombre, int desde, int hasta) {
        for (int k = desde; k < hasta; k++) {
            Cuadruplo c = codigo.get(k);
            if (nombre.equals(Cuadruplos.definicion(c)) || nombre.equals(Cuadruplos.arregloEscrito(c))) {
                return true;
            }
        }
        return false;
    }
}
