// Archivo: OptimizacionRegiones.java
//
// F5. Optimizacion de regiones.
//
// Planificacion: la lista de regiones son los bucles, del mas interno al mas
// externo, y al final el programa entero. Para cada region se calculan sus
// vectores A (asignadas), R (referidas) y B (usadas antes de asignarse): A y R
// son la union de los de sus bloques; B se recalcula por caminos. Las fases
// locales (F1-F3) ya trabajan bloque por bloque y F4 llena el bloque de
// iniciacion de cada bucle; esta fase aporta lo que solo se ve mirando varios
// bloques a la vez:
//
// 1. Propagacion de constantes entre bloques. Analisis de flujo hacia adelante:
//    a la entrada de un bloque, un nombre vale una constante si vale esa misma
//    constante a la salida de TODOS sus predecesores. Con eso se sustituyen sus
//    usos (como las filas 1-2 de la Tabla 7.1, pero cruzando saltos); F1 hace
//    el resto en la siguiente vuelta.
// 2. Eliminacion de bloques inalcanzables: los que deja un salto decidido al
//    compilar (por ejemplo, el cuerpo de un SI (FALSO)).
// 3. Mirilla de saltos: ir_a a la instruccion siguiente, ir_a a otro ir_a,
//    "si c ir_a L1 ; ir_a L2 ; L1:" -> "si no c ir_a L2", y etiquetas a las
//    que no salta nadie.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.Cuadruplo;
import backend.GeneradorCodigo;

final class OptimizacionRegiones implements Fase {

    private boolean planRegistrado = false;

    public String clave() {
        return "F5";
    }

    public String nombre() {
        return "Optimizacion de regiones";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        if (!planRegistrado) {
            registrarPlan(programa, bitacora);
            planRegistrado = true;
        }
        boolean algo = false;
        boolean cambio = true;
        while (cambio) {
            cambio = propagarConstantes(programa, bitacora);
            cambio |= quitarInalcanzables(programa, bitacora);
            cambio |= saltos(programa, bitacora);
            algo |= cambio;
            Cuadruplos.renumerar(programa.codigo);
        }
        return algo;
    }

    /** La lista de regiones con sus vectores, tal como esta al empezar F5. */
    private void registrarPlan(ProgramaIntermedio programa, Bitacora bitacora) {
        GrafoFlujo grafo = new GrafoFlujo(programa.codigo, Collections.<String>emptySet());
        int k = 1;
        for (GrafoFlujo.Bucle bucle : grafo.bucles) {
            List<Set<String>> v = grafo.vectoresDeRegion(bucle);
            String etiqueta = programa.codigo.get(grafo.bloques.get(bucle.cabecera).inicio).resultado;
            bitacora.registrar(clave(), "region " + k++ + ": bucle " + etiqueta + ", bloques " + bucle.bloques,
                "A=" + v.get(0) + " R=" + v.get(1), "B=" + v.get(2));
        }
        bitacora.registrar(clave(), "region " + k + ": el programa entero, " + grafo.bloques.size() + " bloques",
            "-", "-");
    }

    // ------------------------------------------------------------------
    // 1. Propagacion de constantes entre bloques
    // ------------------------------------------------------------------

    private boolean propagarConstantes(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        int n = grafo.bloques.size();
        if (n == 0) {
            return false;
        }

        // salida[b] = null mientras el bloque no se ha alcanzado en el analisis.
        List<Map<String, String>> salida = new ArrayList<Map<String, String>>();
        for (int i = 0; i < n; i++) {
            salida.add(null);
        }
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (GrafoFlujo.Bloque b : grafo.bloques) {
                if (!b.alcanzable) {
                    continue;
                }
                Map<String, String> t = entrada(grafo, b, salida, programa);
                for (int i = b.inicio; i <= b.fin; i++) {
                    transferir(codigo.get(i), t);
                }
                if (!t.equals(salida.get(b.numero))) {
                    salida.set(b.numero, t);
                    cambio = true;
                }
            }
        }

        // Sustituir los usos con lo que se sabe a la entrada de cada bloque.
        boolean sustituyo = false;
        for (GrafoFlujo.Bloque b : grafo.bloques) {
            if (!b.alcanzable) {
                continue;
            }
            Map<String, String> t = entrada(grafo, b, salida, programa);
            for (int i = b.inicio; i <= b.fin; i++) {
                Cuadruplo c = codigo.get(i);
                String antes = c.comentario;
                if (!t.isEmpty() && sustituir(c, t)) {
                    ProgramaIntermedio.reescribir(c);
                    bitacora.registrar(clave(), "constante que llega de otro bloque", antes, c.comentario);
                    sustituyo = true;
                }
                transferir(c, t);
            }
        }
        return sustituyo;
    }

    /** Constantes a la entrada: las que valen lo mismo a la salida de todos los predecesores. */
    private static Map<String, String> entrada(GrafoFlujo grafo, GrafoFlujo.Bloque b,
                                               List<Map<String, String>> salida, ProgramaIntermedio programa) {
        if (b.numero == 0) {
            return new HashMap<String, String>();
        }
        Map<String, String> t = null;
        for (Integer p : b.predecesores) {
            Map<String, String> sp = salida.get(p.intValue());
            if (sp == null) {
                continue;   // todavia no se sabe nada de ese camino
            }
            if (t == null) {
                t = new HashMap<String, String>(sp);
            } else {
                t.entrySet().retainAll(sp.entrySet());
            }
        }
        return (t == null) ? new HashMap<String, String>() : t;
    }

    /** Lo que una instruccion le hace a la tabla: la fila 4 y cualquier otra escritura. */
    private static void transferir(Cuadruplo c, Map<String, String> t) {
        String d = Cuadruplos.definicion(c);
        if (d == null) {
            return;
        }
        t.remove(d);
        if ("=".equals(c.operador) && Cuadruplos.esLiteral(c.arg1)) {
            t.put(d, c.arg1);
        }
    }

    private static boolean sustituir(Cuadruplo c, Map<String, String> t) {
        String op = c.operador;
        if (Cuadruplos.esEtiqueta(c) || "ir_a".equals(op) || "leer".equals(op)) {
            return false;
        }
        boolean cambio = false;
        if (!"[]=".equals(op) && !"=[]".equals(op) && t.containsKey(c.arg1)) {
            c.arg1 = t.get(c.arg1);
            cambio = true;
        }
        if (t.containsKey(c.arg2)) {
            c.arg2 = t.get(c.arg2);
            cambio = true;
        }
        if ("[]=".equals(op) && t.containsKey(c.resultado)) {
            c.resultado = t.get(c.resultado);
            cambio = true;
        }
        return cambio;
    }

    // ------------------------------------------------------------------
    // 2. Bloques inalcanzables
    // ------------------------------------------------------------------

    private boolean quitarInalcanzables(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        List<Cuadruplo> quedan = new ArrayList<Cuadruplo>();
        boolean cambio = false;
        for (GrafoFlujo.Bloque b : grafo.bloques) {
            for (int i = b.inicio; i <= b.fin; i++) {
                if (b.alcanzable) {
                    quedan.add(codigo.get(i));
                } else {
                    bitacora.registrar(clave(), "bloque inalcanzable B" + b.numero, codigo.get(i).comentario, "(eliminada)");
                    cambio = true;
                }
            }
        }
        if (cambio) {
            codigo.clear();
            codigo.addAll(quedan);
        }
        return cambio;
    }

    // ------------------------------------------------------------------
    // 3. Mirilla de saltos
    // ------------------------------------------------------------------

    private boolean saltos(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;

        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            if (!Cuadruplos.esSalto(c)) {
                continue;
            }
            // ir_a a la instruccion siguiente (pasando solo por etiquetas).
            if ("ir_a".equals(c.operador) && caeEn(codigo, i + 1, c.resultado)) {
                bitacora.registrar(clave(), "salto a la instruccion siguiente", c.comentario, "(eliminada)");
                codigo.remove(i);
                return true;
            }
            // Salto a un ir_a: se va directo a su destino.
            String destino = destinoFinal(codigo, c.resultado);
            if (!destino.equals(c.resultado)) {
                String antes = c.comentario;
                c.resultado = destino;
                ProgramaIntermedio.reescribir(c);
                bitacora.registrar(clave(), "salto a un salto", antes, c.comentario);
                return true;
            }
            // si c ir_a L1 ; ir_a L2 ; L1:  ->  si no c ir_a L2 ; L1:
            if (Cuadruplos.esSaltoCondicional(c) && i + 1 < codigo.size()
                    && "ir_a".equals(codigo.get(i + 1).operador) && caeEn(codigo, i + 2, c.resultado)) {
                String contrario = contrario(c.operador);
                if (contrario != null) {
                    Cuadruplo siguiente = codigo.get(i + 1);
                    String antes = c.comentario + " ; " + siguiente.comentario;
                    c.operador = contrario;
                    c.resultado = siguiente.resultado;
                    ProgramaIntermedio.reescribir(c);
                    codigo.remove(i + 1);
                    bitacora.registrar(clave(), "salto sobre un salto", antes, c.comentario);
                    return true;
                }
            }
        }

        // Etiquetas a las que no salta nadie.
        Set<String> usadas = new HashSet<String>();
        for (Cuadruplo c : codigo) {
            if (Cuadruplos.esSalto(c)) {
                usadas.add(c.resultado);
            }
        }
        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            if (Cuadruplos.esEtiqueta(c) && !usadas.contains(c.resultado)) {
                bitacora.registrar(clave(), "etiqueta sin uso", c.comentario, "(eliminada)");
                codigo.remove(i);
                return true;
            }
        }
        return false;
    }

    /** true si desde i, pasando solo por etiquetas, se llega a "etiqueta:". */
    private static boolean caeEn(List<Cuadruplo> codigo, int i, String etiqueta) {
        for (int k = i; k < codigo.size() && Cuadruplos.esEtiqueta(codigo.get(k)); k++) {
            if (codigo.get(k).resultado.equals(etiqueta)) {
                return true;
            }
        }
        return false;
    }

    /** Sigue la cadena "L: ir_a M" hasta un destino que no sea otro ir_a. */
    private static String destinoFinal(List<Cuadruplo> codigo, String etiqueta) {
        String actual = etiqueta;
        Set<String> vistas = new HashSet<String>();
        while (vistas.add(actual)) {
            int k = posicionDe(codigo, actual);
            if (k < 0) {
                return actual;
            }
            while (k < codigo.size() && Cuadruplos.esEtiqueta(codigo.get(k))) {
                k++;
            }
            if (k >= codigo.size() || !"ir_a".equals(codigo.get(k).operador)) {
                return actual;
            }
            actual = codigo.get(k).resultado;
        }
        return etiqueta;   // ciclo de saltos: se deja como estaba
    }

    private static int posicionDe(List<Cuadruplo> codigo, String etiqueta) {
        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            if (Cuadruplos.esEtiqueta(c) && c.resultado.equals(etiqueta)) {
                return i;
            }
        }
        return -1;
    }

    /** El salto condicional con la condicion contraria, o null si no hay. */
    private static String contrario(String op) {
        if ("si_falso".equals(op)) return "si_verdadero";
        if ("si_verdadero".equals(op)) return "si_falso";
        if ("si_igual".equals(op)) return "si_!=";
        String relop = op.substring(3);
        String c = GeneradorCodigo.complemento(relop);
        return c.equals(relop) ? null : "si_" + c;
    }
}
