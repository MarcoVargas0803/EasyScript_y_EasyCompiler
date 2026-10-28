// Archivo: GrafoFlujo.java
//
// Grafo de flujo del codigo intermedio: bloques basicos, sus aristas,
// dominadores, bucles naturales, los vectores A/R/B de cada bloque y las
// variables vivas a la entrada y a la salida de cada uno.
//
// Bloques basicos
// ---------------
// Un lider es la primera instruccion, toda etiqueta y toda instruccion que
// sigue a un salto. Un bloque va de un lider hasta antes del siguiente. Dentro
// de un bloque las instrucciones se ejecutan siempre todas y en orden.
//
// Vectores (por bloque, sobre los nombres del programa)
// -----------------------------------------------------
//   A[v]  v recibe alguna asignacion en el bloque (tambien un elemento de un
//         arreglo: el arreglo cambia).
//   R[v]  v se usa en el bloque.
//   B[v]  v se usa antes de recibir una asignacion COMPLETA. Escribir un
//         elemento no cuenta: el resto del arreglo sigue teniendo su valor
//         anterior, asi que un uso posterior del arreglo sigue dependiendo de
//         lo que habia antes del bloque.
//
// Vivacidad
// ---------
// vivasEntrada = B U (vivasSalida - definidas por completo)
// vivasSalida  = union de vivasEntrada de los sucesores, o el conjunto que se
//                indique para la salida del programa (las variables del usuario).
package backend.optimizacion;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import backend.Cuadruplo;

final class GrafoFlujo {

    static final class Bloque {
        final int numero;
        final int inicio;        // primera instruccion
        final int fin;           // ultima instruccion (incluida)
        final List<Integer> sucesores = new ArrayList<Integer>();
        final List<Integer> predecesores = new ArrayList<Integer>();
        final Set<String> asignadas = new TreeSet<String>();    // A
        final Set<String> referidas = new TreeSet<String>();    // R
        final Set<String> antes = new TreeSet<String>();        // B
        final Set<String> definidas = new TreeSet<String>();    // asignacion completa
        Set<String> vivasEntrada = new TreeSet<String>();
        Set<String> vivasSalida = new TreeSet<String>();
        boolean alcanzable;

        Bloque(int numero, int inicio, int fin) {
            this.numero = numero;
            this.inicio = inicio;
            this.fin = fin;
        }
    }

    /** Bucle natural: una cabecera y los bloques desde los que se vuelve a ella. */
    static final class Bucle {
        final int cabecera;
        final TreeSet<Integer> bloques = new TreeSet<Integer>();
        final List<Integer> retrocesos = new ArrayList<Integer>();   // bloques con la arista de vuelta

        Bucle(int cabecera) {
            this.cabecera = cabecera;
        }

        boolean contiene(int bloque) {
            return bloques.contains(Integer.valueOf(bloque));
        }
    }

    final List<Cuadruplo> codigo;
    final List<Bloque> bloques = new ArrayList<Bloque>();
    final List<Bucle> bucles = new ArrayList<Bucle>();
    private final int[] bloqueDe;
    private final Map<String, Integer> bloqueDeEtiqueta = new HashMap<String, Integer>();
    private BitSet[] dominadores;

    /**
     * @param vivasAlFinal nombres que se consideran vivos al terminar el programa
     *                     (las variables del usuario: su valor final es observable).
     */
    GrafoFlujo(List<Cuadruplo> codigo, Collection<String> vivasAlFinal) {
        this.codigo = codigo;
        this.bloqueDe = new int[codigo.size()];
        construirBloques();
        construirAristas();
        marcarAlcanzables();
        calcularDominadores();
        encontrarBucles();
        calcularVectores();
        calcularVivacidad(vivasAlFinal);
    }

    // ------------------------------------------------------------------
    // Construccion
    // ------------------------------------------------------------------

    private void construirBloques() {
        int n = codigo.size();
        boolean[] lider = new boolean[n];
        for (int i = 0; i < n; i++) {
            Cuadruplo c = codigo.get(i);
            if (i == 0 || Cuadruplos.esEtiqueta(c)) {
                lider[i] = true;
            }
            if (Cuadruplos.esSalto(c) && i + 1 < n) {
                lider[i + 1] = true;
            }
        }
        int inicio = 0;
        for (int i = 1; i <= n; i++) {
            if (i == n || lider[i]) {
                Bloque b = new Bloque(bloques.size(), inicio, i - 1);
                for (int k = inicio; k < i; k++) {
                    bloqueDe[k] = b.numero;
                    Cuadruplo c = codigo.get(k);
                    if (Cuadruplos.esEtiqueta(c)) {
                        bloqueDeEtiqueta.put(c.resultado, Integer.valueOf(b.numero));
                    }
                }
                bloques.add(b);
                inicio = i;
            }
        }
    }

    private void construirAristas() {
        for (Bloque b : bloques) {
            Cuadruplo ultimo = codigo.get(b.fin);
            boolean cae = !"ir_a".equals(ultimo.operador);
            if (Cuadruplos.esSalto(ultimo)) {
                Integer destino = bloqueDeEtiqueta.get(ultimo.resultado);
                if (destino != null) {
                    unir(b.numero, destino.intValue());
                }
            }
            if (cae && b.numero + 1 < bloques.size()) {
                unir(b.numero, b.numero + 1);
            }
        }
    }

    private void unir(int de, int a) {
        Bloque origen = bloques.get(de);
        if (!origen.sucesores.contains(Integer.valueOf(a))) {
            origen.sucesores.add(Integer.valueOf(a));
            bloques.get(a).predecesores.add(Integer.valueOf(de));
        }
    }

    private void marcarAlcanzables() {
        if (bloques.isEmpty()) {
            return;
        }
        List<Integer> pendientes = new ArrayList<Integer>();
        pendientes.add(Integer.valueOf(0));
        bloques.get(0).alcanzable = true;
        while (!pendientes.isEmpty()) {
            Bloque b = bloques.get(pendientes.remove(pendientes.size() - 1).intValue());
            for (Integer s : b.sucesores) {
                Bloque sb = bloques.get(s.intValue());
                if (!sb.alcanzable) {
                    sb.alcanzable = true;
                    pendientes.add(s);
                }
            }
        }
    }

    /** Dominadores por el metodo iterativo, solo entre bloques alcanzables. */
    private void calcularDominadores() {
        int n = bloques.size();
        dominadores = new BitSet[n];
        for (int i = 0; i < n; i++) {
            dominadores[i] = new BitSet(n);
            if (i == 0 || !bloques.get(i).alcanzable) {
                dominadores[i].set(i);
            } else {
                dominadores[i].set(0, n);
            }
        }
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (int i = 1; i < n; i++) {
                Bloque b = bloques.get(i);
                if (!b.alcanzable) {
                    continue;
                }
                BitSet nuevo = null;
                for (Integer p : b.predecesores) {
                    if (!bloques.get(p.intValue()).alcanzable) {
                        continue;
                    }
                    if (nuevo == null) {
                        nuevo = (BitSet) dominadores[p.intValue()].clone();
                    } else {
                        nuevo.and(dominadores[p.intValue()]);
                    }
                }
                if (nuevo == null) {
                    nuevo = new BitSet(n);
                }
                nuevo.set(i);
                if (!nuevo.equals(dominadores[i])) {
                    dominadores[i] = nuevo;
                    cambio = true;
                }
            }
        }
    }

    /** true si el bloque a domina al bloque b. */
    boolean domina(int a, int b) {
        return dominadores[b].get(a);
    }

    /**
     * Una arista b -> h donde h domina a b es de retroceso; el bucle natural son
     * h y todos los bloques que llegan a b sin pasar por h. Los bucles con la
     * misma cabecera se juntan en uno. Quedan ordenados del mas interno (menos
     * bloques) al mas externo.
     */
    private void encontrarBucles() {
        Map<Integer, Bucle> porCabecera = new HashMap<Integer, Bucle>();
        for (Bloque b : bloques) {
            if (!b.alcanzable) {
                continue;
            }
            for (Integer s : b.sucesores) {
                int h = s.intValue();
                if (!domina(h, b.numero)) {
                    continue;
                }
                Bucle bucle = porCabecera.get(s);
                if (bucle == null) {
                    bucle = new Bucle(h);
                    bucle.bloques.add(s);
                    porCabecera.put(s, bucle);
                }
                bucle.retrocesos.add(Integer.valueOf(b.numero));
                List<Integer> pila = new ArrayList<Integer>();
                if (bucle.bloques.add(Integer.valueOf(b.numero))) {
                    pila.add(Integer.valueOf(b.numero));
                }
                while (!pila.isEmpty()) {
                    Bloque x = bloques.get(pila.remove(pila.size() - 1).intValue());
                    for (Integer p : x.predecesores) {
                        if (bloques.get(p.intValue()).alcanzable && bucle.bloques.add(p)) {
                            pila.add(p);
                        }
                    }
                }
            }
        }
        bucles.addAll(porCabecera.values());
        bucles.sort((x, y) -> x.bloques.size() != y.bloques.size()
            ? Integer.compare(x.bloques.size(), y.bloques.size())
            : Integer.compare(x.cabecera, y.cabecera));
    }

    private void calcularVectores() {
        for (Bloque b : bloques) {
            for (int i = b.inicio; i <= b.fin; i++) {
                Cuadruplo c = codigo.get(i);
                for (String u : Cuadruplos.usos(c)) {
                    b.referidas.add(u);
                    if (!b.definidas.contains(u)) {
                        b.antes.add(u);
                    }
                }
                String d = Cuadruplos.definicion(c);
                if (d != null) {
                    b.asignadas.add(d);
                    b.definidas.add(d);
                }
                String arreglo = Cuadruplos.arregloEscrito(c);
                if (arreglo != null) {
                    b.asignadas.add(arreglo);
                }
            }
        }
    }

    private void calcularVivacidad(Collection<String> vivasAlFinal) {
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (int i = bloques.size() - 1; i >= 0; i--) {
                Bloque b = bloques.get(i);
                Set<String> salida = new TreeSet<String>();
                if (b.sucesores.isEmpty()) {
                    salida.addAll(vivasAlFinal);
                }
                for (Integer s : b.sucesores) {
                    salida.addAll(bloques.get(s.intValue()).vivasEntrada);
                }
                Set<String> entrada = new TreeSet<String>(salida);
                entrada.removeAll(b.definidas);
                entrada.addAll(b.antes);
                if (!salida.equals(b.vivasSalida) || !entrada.equals(b.vivasEntrada)) {
                    b.vivasSalida = salida;
                    b.vivasEntrada = entrada;
                    cambio = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    Bloque bloqueDeInstruccion(int indice) {
        return bloques.get(bloqueDe[indice]);
    }

    /** Bloque que empieza con la etiqueta dada, o null. */
    Bloque bloqueDeEtiqueta(String etiqueta) {
        Integer b = bloqueDeEtiqueta.get(etiqueta);
        return (b == null) ? null : bloques.get(b.intValue());
    }

    /**
     * Nombres vivos justo DESPUES de cada instruccion, recorriendo cada bloque
     * hacia atras desde su vivasSalida.
     */
    List<Set<String>> vivasDespuesDeCadaInstruccion() {
        List<Set<String>> resultado = new ArrayList<Set<String>>();
        for (int i = 0; i < codigo.size(); i++) {
            resultado.add(null);
        }
        for (Bloque b : bloques) {
            Set<String> vivas = new HashSet<String>(b.vivasSalida);
            for (int i = b.fin; i >= b.inicio; i--) {
                resultado.set(i, new HashSet<String>(vivas));
                Cuadruplo c = codigo.get(i);
                String d = Cuadruplos.definicion(c);
                if (d != null) {
                    vivas.remove(d);
                }
                vivas.addAll(Cuadruplos.usos(c));
            }
        }
        return resultado;
    }

    /** Descripcion para --bitacora-optimizacion. */
    List<String> describir() {
        List<String> lineas = new ArrayList<String>();
        lineas.add("Bloques basicos: " + bloques.size() + ", bucles: " + bucles.size());
        for (Bloque b : bloques) {
            StringBuilder sb = new StringBuilder();
            sb.append("  B").append(b.numero).append(" [#").append(b.inicio).append("-#").append(b.fin).append("]");
            if (!b.alcanzable) {
                sb.append(" (inalcanzable)");
            }
            sb.append(" -> ");
            if (b.sucesores.isEmpty()) {
                sb.append("fin");
            }
            for (int k = 0; k < b.sucesores.size(); k++) {
                sb.append(k == 0 ? "" : ", ").append("B").append(b.sucesores.get(k));
            }
            lineas.add(sb.toString());
            lineas.add("      A=" + b.asignadas + "  R=" + b.referidas + "  B=" + b.antes);
        }
        for (Bucle bucle : bucles) {
            StringBuilder sb = new StringBuilder("  Bucle con cabecera B" + bucle.cabecera + ": {");
            boolean primero = true;
            for (Integer x : bucle.bloques) {
                sb.append(primero ? "" : ", ").append("B").append(x);
                primero = false;
            }
            lineas.add(sb.append("}").toString());
        }
        return lineas;
    }
}
