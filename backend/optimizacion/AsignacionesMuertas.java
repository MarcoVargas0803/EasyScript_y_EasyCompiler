// Archivo: AsignacionesMuertas.java
//
// F6. Identificacion y eliminacion de las asignaciones muertas.
//
// Para cada asignacion sospechosa a una variable v:
//
//   1. Se sigue adelante en el mismo bloque. Otra asignacion a v sin un uso en
//      medio: la asignacion esta muerta. Un uso de v: esta viva. Si no ocurre
//      ninguna de las dos, paso 2.
//   2. Se siguen todos los caminos desde el final del bloque mirando los
//      vectores de cada bloque por el que se pasa: B[v] = 1 (se usa antes de
//      asignarse) -> viva; B[v] = 0 y v se asigna en el bloque -> ese camino se
//      abandona. Si se acaban los caminos (o se cierran los bucles) sin que
//      aparezca un uso, esta muerta.
//
// Las variables del usuario estan vivas al terminar el programa: su valor
// final es observable. Los temporales no. Nunca se eliminan LEER (consume una
// entrada), IMPRIMIR, las escrituras en arreglos ni una division que podria
// fallar: quitarlas cambiaria lo que hace el programa. Se repite hasta que no
// quede ninguna, porque eliminar una puede dejar muerta a otra.
//
// Al terminar todas las fases, compactar() reutiliza temporales: dos
// temporales del mismo tipo que nunca estan vivos a la vez pueden compartir
// nombre y direccion. Es el coloreado del grafo de interferencias: t1 y t2
// interfieren si uno se asigna mientras el otro esta vivo.
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

final class AsignacionesMuertas implements Fase {

    public String clave() {
        return "F6";
    }

    public String nombre() {
        return "Asignaciones muertas";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        boolean algo = false;
        while (unaPasada(programa, bitacora)) {
            algo = true;
            Cuadruplos.renumerar(programa.codigo);
        }
        return algo;
    }

    private boolean unaPasada(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        Set<String> delUsuario = variablesDelUsuario(codigo);
        GrafoFlujo grafo = new GrafoFlujo(codigo, delUsuario);

        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            if (!esSospechosa(c)) {
                continue;
            }
            String v = c.resultado;
            String motivo = muerta(codigo, grafo, i, v, delUsuario.contains(v));
            if (motivo != null) {
                bitacora.registrar(clave(), "asignacion muerta (" + motivo + ")", c.comentario, "(eliminada)");
                codigo.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Asignaciones que se pueden quitar si estan muertas. */
    private static boolean esSospechosa(Cuadruplo c) {
        return Cuadruplos.esCalculo(c) && Cuadruplos.esNombre(c.resultado) && !Cuadruplos.puedeFallar(c);
    }

    /**
     * El motivo por el que la asignacion de v en i esta muerta, o null si esta
     * viva.
     */
    private static String muerta(List<Cuadruplo> codigo, GrafoFlujo grafo, int i, String v, boolean delUsuario) {
        // Paso 1: el resto del bloque.
        GrafoFlujo.Bloque bloque = grafo.bloqueDeInstruccion(i);
        for (int k = i + 1; k <= bloque.fin; k++) {
            Cuadruplo c = codigo.get(k);
            if (Cuadruplos.usos(c).contains(v)) {
                return null;
            }
            if (v.equals(Cuadruplos.definicion(c))) {
                return "se vuelve a asignar en #" + k + " sin usarse";
            }
        }
        // Paso 2: los caminos que salen del bloque.
        Set<Integer> visitados = new HashSet<Integer>();
        List<Integer> pendientes = new ArrayList<Integer>();
        if (bloque.sucesores.isEmpty() && delUsuario) {
            return null;   // el programa termina aqui y su valor final cuenta
        }
        pendientes.addAll(bloque.sucesores);
        while (!pendientes.isEmpty()) {
            Integer n = pendientes.remove(pendientes.size() - 1);
            if (!visitados.add(n)) {
                continue;   // bucle cerrado
            }
            GrafoFlujo.Bloque b = grafo.bloques.get(n.intValue());
            if (b.antes.contains(v)) {
                return null;   // B[v] = 1
            }
            if (b.definidas.contains(v)) {
                continue;      // B[v] = 0 y A[v] = 1: se abandona el camino
            }
            if (b.sucesores.isEmpty() && delUsuario) {
                return null;   // llega al final del programa
            }
            pendientes.addAll(b.sucesores);
        }
        return "ningun camino la usa";
    }

    /** Variables, constantes y arreglos del programa: todo nombre que no sea temporal. */
    private static Set<String> variablesDelUsuario(List<Cuadruplo> codigo) {
        Set<String> s = new HashSet<String>();
        for (Cuadruplo c : codigo) {
            for (String x : new String[] { c.arg1, c.arg2, c.resultado }) {
                if (Cuadruplos.esNombre(x) && !Cuadruplos.esTemporal(x) && !Cuadruplos.esEtiqueta(c)
                        && !(Cuadruplos.esSalto(c) && x.equals(c.resultado))) {
                    s.add(x);
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Compactacion de temporales
    // ------------------------------------------------------------------

    /**
     * Reutiliza temporales que no estan vivos a la vez y los renombra t1, t2...
     * por orden de aparicion. Se hace una sola vez, al final: las demas fases
     * suponen que cada temporal tiene un solo valor.
     */
    static void compactar(ProgramaIntermedio programa, Bitacora bitacora) {
        List<Cuadruplo> codigo = programa.codigo;
        GrafoFlujo grafo = new GrafoFlujo(codigo, variablesDelUsuario(codigo));
        List<Set<String>> vivasDespues = grafo.vivasDespuesDeCadaInstruccion();

        // Temporales por orden de aparicion.
        List<String> temporales = new ArrayList<String>();
        for (Cuadruplo c : codigo) {
            for (String x : new String[] { c.arg1, c.arg2, c.resultado }) {
                if (Cuadruplos.esTemporal(x) && !temporales.contains(x)) {
                    temporales.add(x);
                }
            }
        }
        if (temporales.isEmpty()) {
            return;
        }

        // Grafo de interferencias.
        Map<String, Set<String>> interfiere = new HashMap<String, Set<String>>();
        for (String t : temporales) {
            interfiere.put(t, new HashSet<String>());
        }
        for (int i = 0; i < codigo.size(); i++) {
            String d = Cuadruplos.definicion(codigo.get(i));
            if (!Cuadruplos.esTemporal(d)) {
                continue;
            }
            for (String vivo : vivasDespues.get(i)) {
                if (Cuadruplos.esTemporal(vivo) && !vivo.equals(d)) {
                    interfiere.get(d).add(vivo);
                    interfiere.get(vivo).add(d);
                }
            }
        }

        // Coloreado voraz: cada temporal toma el primer nombre libre de su tipo.
        List<List<String>> colores = new ArrayList<List<String>>();
        List<String> tipoDeColor = new ArrayList<String>();
        Map<String, Integer> color = new LinkedHashMap<String, Integer>();
        for (String t : temporales) {
            String tipo = programa.tipoDe(t);
            int elegido = -1;
            for (int k = 0; k < colores.size() && elegido < 0; k++) {
                if (!String.valueOf(tipo).equals(tipoDeColor.get(k))) {
                    continue;
                }
                if (Collections.disjoint(colores.get(k), interfiere.get(t))) {
                    elegido = k;
                }
            }
            if (elegido < 0) {
                colores.add(new ArrayList<String>());
                tipoDeColor.add(String.valueOf(tipo));
                elegido = colores.size() - 1;
            }
            colores.get(elegido).add(t);
            color.put(t, Integer.valueOf(elegido));
        }

        // Renombrar.
        Map<String, String> nuevo = new HashMap<String, String>();
        for (Map.Entry<String, Integer> e : color.entrySet()) {
            nuevo.put(e.getKey(), "t" + (e.getValue().intValue() + 1));
        }
        for (Cuadruplo c : codigo) {
            String a1 = nuevo.get(c.arg1);
            String a2 = nuevo.get(c.arg2);
            String r = nuevo.get(c.resultado);
            if (a1 != null) c.arg1 = a1;
            if (a2 != null) c.arg2 = a2;
            if (r != null && !Cuadruplos.esEtiqueta(c)) c.resultado = r;
            ProgramaIntermedio.reescribir(c);
        }
        for (int k = 0; k < colores.size(); k++) {
            String t = "t" + (k + 1);
            programa.tipos.put(t, tipoDeColor.get(k));
            bitacora.registrar("F6", "compactacion de temporales: " + t + " (" + tipoDeColor.get(k) + ")",
                String.join(", ", colores.get(k)), t);
        }
    }
}
