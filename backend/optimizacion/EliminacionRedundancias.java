// Archivo: EliminacionRedundancias.java
//
// F2. Eliminacion de redundancias.
//
// Algoritmo de dependencias, dentro de cada bloque basico:
//
//   1. Todo nombre empieza con dependencia -1 (las constantes siempre valen -1).
//   2. Para cada cuadruplo i, en orden:
//      a. dep(i) = 1 + max(dep de sus operandos).
//      b. Si i asigna un nombre, dep(nombre) = i.
//      c. i es equivalente a un j anterior si tienen el mismo operador y los
//         mismos operandos, y dep(i) = dep(j). En una asignación ("=") tambien
//         debe coincidir el resultado.
//      d. Si lo es, i pasa a (COMO, j) y en adelante el resultado de i se
//         sustituye por el de j. Las COMO se borran al final.
//
// Que dep(i) = dep(j) garantiza que ningun operando cambio entre j e i: si
// alguno se hubiera reasignado en medio, su dependencia seria mayor que j y la
// de i tambien.
//
// Detalles de este codigo intermedio:
//   - Un arreglo es un nombre mas: escribir un elemento (V[k] = x) actualiza
//     dep(V), asi que una lectura V[k] posterior a esa escritura nunca se
//     confunde con una anterior.
//   - LEER e IMPRIMIR nunca son equivalentes a nada (tienen efectos).
//   - El resultado de j tiene que seguir valiendo lo mismo en i (dep(res j) = j).
//   - La sustitucion de un resultado por otro solo se hace entre temporales
//     con una sola asignacion en todo el programa; en otro caso i se cambia
//     por la copia "res i = res j", que es correcta en ese punto.
//
// Ademas, una mirilla de copias: "t = a op b ; X = t", con t usado solo en la
// copia, pasa a "X = a op b".
package backend.optimizacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import backend.Cuadruplo;

final class EliminacionRedundancias implements Fase {

    private static final String COMO = "COMO";

    public String clave() {
        return "F2";
    }

    public String nombre() {
        return "Eliminacion de redundancias";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean algo = false;
        boolean cambio = true;
        while (cambio) {
            cambio = dependencias(programa, bitacora);
            cambio |= copias(programa, bitacora);
            algo |= cambio;
            Cuadruplos.renumerar(programa.codigo);
        }
        return algo;
    }

    // ------------------------------------------------------------------
    // Algoritmo de dependencias
    // ------------------------------------------------------------------

    private boolean dependencias(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        Map<String, Integer> asignaciones = contarAsignaciones(codigo);
        Map<String, String> sustituir = new HashMap<String, String>();
        boolean cambio = false;

        for (GrafoFlujo.Bloque bloque : grafo.bloques) {
            Map<String, Integer> depNombre = new HashMap<String, Integer>();
            int[] depCuadruplo = new int[codigo.size()];

            for (int i = bloque.inicio; i <= bloque.fin; i++) {
                Cuadruplo c = codigo.get(i);
                if (Cuadruplos.esEtiqueta(c)) {
                    continue;
                }
                // Los usos de un resultado ya sustituido en este bloque.
                aplicarSustituciones(c, sustituir);

                // a. Dependencia de la cuadrupla.
                int dep = -1;
                for (String u : Cuadruplos.usos(c)) {
                    dep = Math.max(dep, dependencia(depNombre, u));
                }
                if ("=[]".equals(c.operador)) {
                    dep = Math.max(dep, dependencia(depNombre, c.arg1));
                }
                dep = dep + 1;
                depCuadruplo[i] = dep;

                // c. Equivalente a una anterior del bloque?
                int j = esCandidato(c) ? buscarEquivalente(codigo, bloque.inicio, i, dep, depCuadruplo, depNombre) : -1;
                if (j >= 0) {
                    Cuadruplo anterior = codigo.get(j);
                    String antes = c.comentario;
                    cambio = true;
                    if ("=".equals(c.operador)) {
                        // La variable ya tiene ese valor: la asignacion sobra.
                        marcarComo(c, j);
                        bitacora.registrar(clave(), "asignacion repetida, (COMO, " + j + ")", antes, "(eliminada)");
                        continue;
                    }
                    if (Cuadruplos.esTemporal(c.resultado) && Cuadruplos.esTemporal(anterior.resultado)
                            && cuenta(asignaciones, c.resultado) == 1 && cuenta(asignaciones, anterior.resultado) == 1) {
                        sustituir.put(c.resultado, anterior.resultado);
                        marcarComo(c, j);
                        bitacora.registrar(clave(), "equivalente a #" + j + ", (COMO, " + j + "): "
                            + antes.split(" = ")[0] + " -> " + anterior.resultado, antes, "(eliminada)");
                        continue;
                    }
                    c.operador = "=";
                    c.arg1 = anterior.resultado;
                    c.arg2 = "-";
                    ProgramaIntermedio.reescribir(c);
                    bitacora.registrar(clave(), "equivalente a #" + j + ", se copia su resultado", antes, c.comentario);
                }

                // b. Dependencia de lo que asigna.
                String d = Cuadruplos.definicion(c);
                if (d != null) {
                    depNombre.put(d, Integer.valueOf(i));
                }
                String arreglo = Cuadruplos.arregloEscrito(c);
                if (arreglo != null) {
                    depNombre.put(arreglo, Integer.valueOf(i));
                }
            }
        }

        // En adelante, en todas las cuadruplas: el resultado de i por el de j.
        if (!sustituir.isEmpty()) {
            for (Cuadruplo c : codigo) {
                if (!COMO.equals(c.operador)) {
                    aplicarSustituciones(c, sustituir);
                }
            }
        }

        // Las COMO solo existian durante el algoritmo.
        List<Cuadruplo> sinComo = new ArrayList<Cuadruplo>();
        for (Cuadruplo c : codigo) {
            if (!COMO.equals(c.operador)) {
                sinComo.add(c);
            }
        }
        codigo.clear();
        codigo.addAll(sinComo);
        return cambio;
    }

    /** Instrucciones que pueden ser equivalentes a otra: calculos y copias. */
    private static boolean esCandidato(Cuadruplo c) {
        return Cuadruplos.esCalculo(c) && Cuadruplos.esNombre(c.resultado);
    }

    private static int buscarEquivalente(List<Cuadruplo> codigo, int inicio, int i, int dep,
                                         int[] depCuadruplo, Map<String, Integer> depNombre) {
        Cuadruplo c = codigo.get(i);
        boolean asignacion = "=".equals(c.operador);
        for (int j = inicio; j < i; j++) {
            Cuadruplo o = codigo.get(j);
            if (COMO.equals(o.operador) || !esCandidato(o)) {
                continue;
            }
            if (!o.operador.equals(c.operador) || !o.arg1.equals(c.arg1) || !o.arg2.equals(c.arg2)) {
                continue;
            }
            if (depCuadruplo[j] != dep) {
                continue;
            }
            if (asignacion && !o.resultado.equals(c.resultado)) {
                continue;
            }
            // El resultado de j no se ha vuelto a asignar desde j.
            if (dependencia(depNombre, o.resultado) != j) {
                continue;
            }
            return j;
        }
        return -1;
    }

    private static int dependencia(Map<String, Integer> depNombre, String nombre) {
        Integer d = depNombre.get(nombre);
        return (d == null) ? -1 : d.intValue();
    }

    private static void marcarComo(Cuadruplo c, int j) {
        c.operador = COMO;
        c.arg1 = String.valueOf(j);
        c.arg2 = "-";
        c.resultado = "-";
        c.comentario = "(COMO, " + j + ")";
    }

    /** Cambia en los operandos cada nombre sustituido por su reemplazo. */
    static void aplicarSustituciones(Cuadruplo c, Map<String, String> sustituir) {
        if (sustituir.isEmpty() || Cuadruplos.esEtiqueta(c) || "ir_a".equals(c.operador) || "leer".equals(c.operador)) {
            return;
        }
        boolean cambio = false;
        String a1 = sustituir.get(c.arg1);
        String a2 = sustituir.get(c.arg2);
        if (a1 != null && !"[]=".equals(c.operador) && !"=[]".equals(c.operador)) {
            c.arg1 = a1;
            cambio = true;
        }
        if (a2 != null) {
            c.arg2 = a2;
            cambio = true;
        }
        if ("[]=".equals(c.operador)) {
            String r = sustituir.get(c.resultado);
            if (r != null) {
                c.resultado = r;
                cambio = true;
            }
        }
        if (cambio) {
            ProgramaIntermedio.reescribir(c);
        }
    }

    // ------------------------------------------------------------------
    // Mirilla de copias
    // ------------------------------------------------------------------

    /** "t = a op b ; X = t" con t usado una sola vez -> "X = a op b". */
    private boolean copias(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        Map<String, Integer> asignaciones = contarAsignaciones(codigo);
        Map<String, Integer> usos = contarUsos(codigo);
        boolean cambio = false;
        for (int i = 0; i + 1 < codigo.size(); i++) {
            Cuadruplo calculo = codigo.get(i);
            Cuadruplo copia = codigo.get(i + 1);
            if (!Cuadruplos.esCalculo(calculo) || !Cuadruplos.esTemporal(calculo.resultado)) {
                continue;
            }
            if (!"=".equals(copia.operador) || !calculo.resultado.equals(copia.arg1)
                    || !Cuadruplos.esNombre(copia.resultado)) {
                continue;
            }
            String t = calculo.resultado;
            if (cuenta(asignaciones, t) != 1 || cuenta(usos, t) != 1) {
                continue;
            }
            String antes = calculo.comentario + " ; " + copia.comentario;
            calculo.resultado = copia.resultado;
            ProgramaIntermedio.reescribir(calculo);
            codigo.remove(i + 1);
            bitacora.registrar(clave(), "copia sobrante (mirilla)", antes, calculo.comentario);
            cambio = true;
        }
        return cambio;
    }

    // ------------------------------------------------------------------
    // Conteos
    // ------------------------------------------------------------------

    static Map<String, Integer> contarAsignaciones(List<Cuadruplo> codigo) {
        Map<String, Integer> n = new HashMap<String, Integer>();
        for (Cuadruplo c : codigo) {
            String d = Cuadruplos.definicion(c);
            if (d != null) {
                n.put(d, Integer.valueOf(cuenta(n, d) + 1));
            }
        }
        return n;
    }

    static Map<String, Integer> contarUsos(List<Cuadruplo> codigo) {
        Map<String, Integer> n = new HashMap<String, Integer>();
        for (Cuadruplo c : codigo) {
            for (String u : Cuadruplos.usos(c)) {
                n.put(u, Integer.valueOf(cuenta(n, u) + 1));
            }
        }
        return n;
    }

    static int cuenta(Map<String, Integer> n, String nombre) {
        Integer v = n.get(nombre);
        return (v == null) ? 0 : v.intValue();
    }
}
