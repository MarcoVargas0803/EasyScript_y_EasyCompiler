// Archivo: PlegadoConstantes.java
//
// F1. Ejecucion en tiempo de compilacion.
//
// Recorre el codigo bloque por bloque con una tabla T de pares (nombre, valor)
// que se vacia al empezar cada bloque basico: lo que se sabe de un nombre solo
// vale mientras no haya un salto de por medio. A cada cuadruplo se le aplica la
// primera fila que cumpla de la Tabla 7.1:
//
//   1-2. Un operando que esta en T se sustituye por su valor.
//   3.   Si todos los operandos son constantes se calcula el resultado. Si el
//        calculo falla (division entre cero) se avisa y la instruccion se
//        deja: puede que nunca se ejecute. Si no, (res, valor) entra en T y la
//        instruccion se elimina... siempre que res sea un temporal que solo se
//        use en este bloque; si no, queda como copia "res = valor", porque
//        alguien fuera del bloque necesita encontrarlo escrito.
//   4.   "res = v" saca a res de T y, si v es constante, mete (res, v).
//
// Cualquier otra escritura de un nombre (leer, =[] ...) lo saca de T.
//
// Dos extensiones con la misma idea:
//   - Las constantes CONST_ tienen una sola asignacion en todo el programa: si
//     es un valor constante, se sustituyen en cualquier bloque.
//   - Un salto condicional con operandos constantes se decide al compilar: se
//     convierte en ir_a o desaparece.
//
// Los valores se calculan con SimuladorCuadruplos.evaluar, la misma funcion
// que ejecuta el codigo, y solo se pliega un resultado que se pueda escribir
// como literal y volver a leer igual (un DEC como 1.0E10 no se pliega).
package backend.optimizacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import backend.Cuadruplo;
import backend.ErrorCompilador;
import backend.SimuladorCuadruplos;

final class PlegadoConstantes implements Fase {

    public String clave() {
        return "F1";
    }

    public String nombre() {
        return "Ejecucion en tiempo de compilacion";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean algo = false;
        // "El resultado de cada cuadrupla se trata reiteradamente hasta que no
        // se produce ningun cambio."
        while (pasada(programa, bitacora)) {
            algo = true;
            Cuadruplos.renumerar(programa.codigo);
        }
        return algo;
    }

    private boolean pasada(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, java.util.Collections.<String>emptySet());
        Map<String, String> globales = constantesGlobales(codigo, programa);
        Map<String, List<Integer>> usosDe = usos(codigo);

        Map<String, String> t = new HashMap<String, String>();
        List<Cuadruplo> nuevo = new ArrayList<Cuadruplo>();
        boolean cambio = false;

        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            GrafoFlujo.Bloque bloque = grafo.bloqueDeInstruccion(i);
            if (bloque.inicio == i) {
                t.clear();
            }

            // Filas 1 y 2: operandos conocidos.
            String antes = c.comentario;
            if (sustituir(c, t, globales)) {
                ProgramaIntermedio.reescribir(c);
                bitacora.registrar(clave(), "Tabla 7.1 filas 1-2, operando conocido", antes, c.comentario);
                cambio = true;
            }

            // Salto con operandos constantes: se decide ahora.
            if (Cuadruplos.esSaltoCondicional(c) && operandosConstantes(c)) {
                Boolean salta = decidirSalto(c);
                if (salta != null) {
                    antes = c.comentario;
                    cambio = true;
                    if (salta.booleanValue()) {
                        c.operador = "ir_a";
                        c.arg1 = "-";
                        c.arg2 = "-";
                        ProgramaIntermedio.reescribir(c);
                        bitacora.registrar(clave(), "salto decidido al compilar (siempre salta)", antes, c.comentario);
                        nuevo.add(c);
                    } else {
                        bitacora.registrar(clave(), "salto decidido al compilar (nunca salta)", antes, "(eliminada)");
                    }
                    continue;
                }
            }

            // Fila 3: operacion con todos sus operandos constantes.
            if (esPlegable(c) && operandosConstantes(c)) {
                Object valor;
                try {
                    valor = SimuladorCuadruplos.evaluar(c.operador,
                        SimuladorCuadruplos.valorLiteral(c.arg1),
                        Cuadruplos.vacia(c.arg2) ? null : SimuladorCuadruplos.valorLiteral(c.arg2));
                } catch (RuntimeException e) {
                    avisar(programa, bitacora, c, e.getMessage());
                    t.remove(c.resultado);
                    nuevo.add(c);
                    continue;
                }
                String literal = escribir(valor);
                if (literal == null) {
                    t.remove(c.resultado);
                    nuevo.add(c);
                    continue;
                }
                antes = c.comentario;
                t.put(c.resultado, literal);
                cambio = true;
                if (Cuadruplos.esTemporal(c.resultado) && soloSeUsaEn(usosDe.get(c.resultado), bloque, grafo)) {
                    bitacora.registrar(clave(), "Tabla 7.1 fila 3, calculado: " + c.resultado + " = " + literal,
                        antes, "(eliminada)");
                    continue;
                }
                c.operador = "=";
                c.arg1 = literal;
                c.arg2 = "-";
                ProgramaIntermedio.reescribir(c);
                bitacora.registrar(clave(), "Tabla 7.1 fila 3, calculado (se usa fuera del bloque)", antes, c.comentario);
                nuevo.add(c);
                continue;
            }

            // Fila 4 y cualquier otra escritura.
            String definido = Cuadruplos.definicion(c);
            if (definido != null) {
                t.remove(definido);
                if ("=".equals(c.operador) && Cuadruplos.esLiteral(c.arg1)) {
                    t.put(definido, c.arg1);
                }
            }
            nuevo.add(c);
        }

        codigo.clear();
        codigo.addAll(nuevo);
        return cambio;
    }

    // ------------------------------------------------------------------
    // Filas 1 y 2
    // ------------------------------------------------------------------

    /** Sustituye por su valor cada operando que este en T o sea una constante global. */
    private static boolean sustituir(Cuadruplo c, Map<String, String> t, Map<String, String> globales) {
        String op = c.operador;
        if (Cuadruplos.esEtiqueta(c) || "ir_a".equals(op) || "leer".equals(op)) {
            return false;
        }
        boolean cambio = false;
        if ("[]=".equals(op)) {
            String a2 = valorDe(c.arg2, t, globales);
            String r = valorDe(c.resultado, t, globales);
            cambio = a2 != null || r != null;
            if (a2 != null) c.arg2 = a2;
            if (r != null) c.resultado = r;
            return cambio;
        }
        if (!"=[]".equals(op)) {
            String a1 = valorDe(c.arg1, t, globales);
            if (a1 != null) {
                c.arg1 = a1;
                cambio = true;
            }
        }
        String a2 = valorDe(c.arg2, t, globales);
        if (a2 != null) {
            c.arg2 = a2;
            cambio = true;
        }
        return cambio;
    }

    private static String valorDe(String casilla, Map<String, String> t, Map<String, String> globales) {
        if (!Cuadruplos.esNombre(casilla)) {
            return null;
        }
        String v = t.get(casilla);
        return (v != null) ? v : globales.get(casilla);
    }

    /**
     * Constantes CONST_ cuya unica asignacion es un valor constante. Al no
     * poder reasignarse, su valor vale en todo el programa.
     */
    private static Map<String, String> constantesGlobales(List<Cuadruplo> codigo, ProgramaIntermedio programa) {
        Map<String, String> valores = new HashMap<String, String>();
        Map<String, Integer> asignaciones = new HashMap<String, Integer>();
        for (Cuadruplo c : codigo) {
            String d = Cuadruplos.definicion(c);
            if (d == null || !programa.constantes.contains(d)) {
                continue;
            }
            Integer n = asignaciones.get(d);
            asignaciones.put(d, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
            if ("=".equals(c.operador) && Cuadruplos.esLiteral(c.arg1)) {
                valores.put(d, c.arg1);
            }
        }
        for (Map.Entry<String, Integer> e : asignaciones.entrySet()) {
            if (e.getValue().intValue() != 1) {
                valores.remove(e.getKey());
            }
        }
        return valores;
    }

    // ------------------------------------------------------------------
    // Fila 3
    // ------------------------------------------------------------------

    /** Operaciones que calculan un valor solo a partir de sus operandos. */
    private static boolean esPlegable(Cuadruplo c) {
        return Cuadruplos.esCalculo(c) && !"=".equals(c.operador) && !"=[]".equals(c.operador)
            && Cuadruplos.esNombre(c.resultado);
    }

    /** true si todos los operandos de la instruccion son literales. */
    private static boolean operandosConstantes(Cuadruplo c) {
        if (!Cuadruplos.esLiteral(c.arg1)) {
            return false;
        }
        return Cuadruplos.vacia(c.arg2) || Cuadruplos.esLiteral(c.arg2);
    }

    /** true si salta, false si no, null si no se puede decidir. */
    private static Boolean decidirSalto(Cuadruplo c) {
        try {
            return Boolean.valueOf(SimuladorCuadruplos.saltaria(c.operador,
                SimuladorCuadruplos.valorLiteral(c.arg1),
                Cuadruplos.vacia(c.arg2) ? null : SimuladorCuadruplos.valorLiteral(c.arg2)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Escribe un valor como literal, o null si no se puede leer de vuelta igual. */
    static String escribir(Object v) {
        String s;
        if (v instanceof Boolean) {
            s = ((Boolean) v).booleanValue() ? "VERDADERO" : "FALSO";
        } else if (v instanceof Character) {
            s = "'" + v + "'";
        } else if (v instanceof String) {
            s = "\"" + escapar((String) v) + "\"";
        } else if (v instanceof Long || v instanceof Double) {
            s = v.toString();
        } else {
            return null;
        }
        try {
            Object deVuelta = SimuladorCuadruplos.valorLiteral(s);
            return (deVuelta != null && deVuelta.equals(v)) ? s : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String escapar(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default:   sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Indices de las instrucciones que leen cada nombre. */
    private static Map<String, List<Integer>> usos(List<Cuadruplo> codigo) {
        Map<String, List<Integer>> usos = new HashMap<String, List<Integer>>();
        for (int i = 0; i < codigo.size(); i++) {
            for (String u : Cuadruplos.usos(codigo.get(i))) {
                List<Integer> l = usos.get(u);
                if (l == null) {
                    l = new ArrayList<Integer>();
                    usos.put(u, l);
                }
                l.add(Integer.valueOf(i));
            }
        }
        return usos;
    }

    private static boolean soloSeUsaEn(List<Integer> usos, GrafoFlujo.Bloque bloque, GrafoFlujo grafo) {
        if (usos == null) {
            return true;
        }
        for (Integer i : usos) {
            if (grafo.bloqueDeInstruccion(i.intValue()) != bloque) {
                return false;
            }
        }
        return true;
    }

    /** Advertencia OPT-01, una sola vez por instruccion aunque la fase se repita. */
    private void avisar(ProgramaIntermedio programa, Bitacora bitacora, Cuadruplo c, String motivo) {
        if (!programa.avisados.add(c)) {
            return;
        }
        bitacora.registrar(clave(), "Tabla 7.1 fila 3, el calculo falla (" + motivo + "): se deja",
            c.comentario, c.comentario);
        String donde = (c.fuente == null) ? "Una operacion" : "La operacion de '" + c.fuente + "'";
        programa.advertencias.add(new ErrorCompilador("Advertencia", "OPT-01", c.linea, c.columna,
            donde + " da " + motivo + " al calcularla en tiempo de compilacion ("
                + calculo(c) + "). Se deja como esta: solo falla si llega a ejecutarse.",
            "Revisa la expresion. Si esta en codigo que nunca se ejecuta (por ejemplo, dentro de un SI (FALSO)), puedes quitarla."));
    }

    /** "1 / 0": la operacion con sus operandos ya calculados, sin el temporal. */
    private static String calculo(Cuadruplo c) {
        return Cuadruplos.vacia(c.arg2) ? c.operador + " " + c.arg1 : c.arg1 + " " + c.operador + " " + c.arg2;
    }
}
