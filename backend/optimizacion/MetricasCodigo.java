// Archivo: MetricasCodigo.java
//
// Medidas de un programa en codigo intermedio, para comparar antes y despues de
// optimizar. Hay tres grupos:
//
//   Estaticas  (contando el texto del codigo)
//     instrucciones, etiquetas, saltos, temporales distintos, bytes que ocupan
//     los temporales, y el marco = datos + temporales. El lenguaje no tiene
//     funciones, asi que el marco es toda la memoria de pila del programa.
//
//   Registros
//     maximo de temporales vivos a la vez (los que tendrian que estar en
//     registros al mismo tiempo) y el minimo de variables auxiliares que pide la
//     expresion mas exigente: cada hoja vale 0; un nodo cuyos hijos valen todos
//     j vale j+1; si no, el maximo de sus hijos.
//
//   Dinamicas  (ejecutando el codigo en SimuladorCuadruplos, entradas fijas)
//     instrucciones ejecutadas, costo ponderado con CostosEjecucion, y lecturas
//     y escrituras de memoria.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import backend.Cuadruplo;
import backend.SimuladorCuadruplos;
import backend.Tipo;

public final class MetricasCodigo {

    // Estaticas
    public int instrucciones;
    public int etiquetas;
    public int saltos;
    public int temporales;
    public int bytesTemporales;
    public int bytesDatos;
    public int costoEstatico;

    // Registros
    public int maxTemporalesVivos;
    public int minimoAuxiliares;

    // Dinamicas (solo si simulado)
    public boolean simulado;
    public String terminacion = "-";
    public int pasos;
    public long costoDinamico;
    public long lecturas;
    public long escrituras;

    /** La traza del simulador, para comprobar que el programa hace lo mismo. */
    List<String> traza = Collections.emptyList();

    public int marco() {
        return bytesDatos + bytesTemporales;
    }

    /**
     * @param tipos   tipo de cada temporal (para su ancho)
     * @param datos   bytes de las variables del programa (tamano del marco de datos)
     * @param simular true para ejecutar el codigo y tener las metricas dinamicas
     */
    static MetricasCodigo medir(List<Cuadruplo> codigo, Map<String, String> tipos, int datos,
                                CostosEjecucion costos, boolean simular) {
        MetricasCodigo m = new MetricasCodigo();
        m.bytesDatos = datos;

        Set<String> temps = new TreeSet<String>();
        for (Cuadruplo c : codigo) {
            if (Cuadruplos.esEtiqueta(c)) {
                m.etiquetas++;
                continue;
            }
            m.instrucciones++;
            m.costoEstatico += costos.costo(c.operador);
            if (Cuadruplos.esSalto(c)) {
                m.saltos++;
            }
            for (String s : new String[] { c.arg1, c.arg2, c.resultado }) {
                if (Cuadruplos.esTemporal(s)) {
                    temps.add(s);
                }
            }
        }
        m.temporales = temps.size();
        for (String t : temps) {
            m.bytesTemporales += anchoDe(tipos.get(t));
        }

        GrafoFlujo grafo = new GrafoFlujo(codigo, Collections.<String>emptySet());
        for (Set<String> vivas : grafo.vivasDespuesDeCadaInstruccion()) {
            int n = 0;
            for (String v : vivas) {
                if (Cuadruplos.esTemporal(v)) {
                    n++;
                }
            }
            m.maxTemporalesVivos = Math.max(m.maxTemporalesVivos, n);
        }
        m.minimoAuxiliares = minimoAuxiliares(codigo);

        if (simular) {
            SimuladorCuadruplos sim = new SimuladorCuadruplos(codigo);
            m.traza = sim.ejecutar();
            m.simulado = true;
            m.terminacion = sim.getTerminacion();
            m.pasos = sim.getPasos();
            m.lecturas = sim.getLecturasMemoria();
            m.escrituras = sim.getEscriturasMemoria();
            for (Map.Entry<String, Integer> e : sim.getEjecucionesPorOperador().entrySet()) {
                m.costoDinamico += (long) costos.costo(e.getKey()) * e.getValue().intValue();
            }
        }
        return m;
    }

    /**
     * Etiquetado de cada valor intermedio con el numero de auxiliares que
     * necesita: las hojas (variables y literales) valen 0; un nodo cuyos hijos
     * valen todos j vale j+1; si no, vale el maximo de sus hijos. El resultado
     * es el mayor de todos.
     */
    static int minimoAuxiliares(List<Cuadruplo> codigo) {
        Map<String, Integer> etiqueta = new HashMap<String, Integer>();
        int maximo = 0;
        for (Cuadruplo c : codigo) {
            if (!Cuadruplos.esCalculo(c) || !Cuadruplos.esTemporal(c.resultado)) {
                continue;
            }
            List<Integer> hijos = new ArrayList<Integer>();
            for (String s : new String[] { c.arg1, c.arg2 }) {
                if (Cuadruplos.vacia(s)) {
                    continue;
                }
                Integer e = Cuadruplos.esTemporal(s) ? etiqueta.get(s) : null;
                hijos.add(Integer.valueOf(e == null ? 0 : e.intValue()));
            }
            int valor = 0;
            if (!hijos.isEmpty()) {
                boolean iguales = new HashSet<Integer>(hijos).size() == 1;
                valor = iguales ? hijos.get(0).intValue() + 1 : Collections.max(hijos).intValue();
            }
            etiqueta.put(c.resultado, Integer.valueOf(valor));
            maximo = Math.max(maximo, valor);
        }
        return maximo;
    }

    private static int anchoDe(String tipo) {
        if (tipo == null) {
            return 0;
        }
        try {
            return Tipo.desdeLexema(tipo).ancho;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Presentacion
    // ------------------------------------------------------------------

    /** Filas {metrica, antes, despues, cambio} para imprimir una comparacion. */
    public static List<String[]> comparar(MetricasCodigo a, MetricasCodigo b) {
        List<String[]> filas = new ArrayList<String[]>();
        fila(filas, "Instrucciones", a.instrucciones, b.instrucciones);
        fila(filas, "Etiquetas", a.etiquetas, b.etiquetas);
        fila(filas, "Saltos", a.saltos, b.saltos);
        fila(filas, "Temporales", a.temporales, b.temporales);
        fila(filas, "Bytes de temporales", a.bytesTemporales, b.bytesTemporales);
        fila(filas, "Marco (datos + temp.)", a.marco(), b.marco());
        fila(filas, "Costo estatico", a.costoEstatico, b.costoEstatico);
        fila(filas, "Max. temporales vivos", a.maxTemporalesVivos, b.maxTemporalesVivos);
        fila(filas, "Minimo de auxiliares", a.minimoAuxiliares, b.minimoAuxiliares);
        if (a.simulado && b.simulado) {
            fila(filas, "Instr. ejecutadas", a.pasos, b.pasos);
            fila(filas, "Costo dinamico", a.costoDinamico, b.costoDinamico);
            fila(filas, "Lecturas de memoria", a.lecturas, b.lecturas);
            fila(filas, "Escrituras de memoria", a.escrituras, b.escrituras);
        }
        return filas;
    }

    private static void fila(List<String[]> filas, String nombre, long antes, long despues) {
        filas.add(new String[] { nombre, String.valueOf(antes), String.valueOf(despues), cambio(antes, despues) });
    }

    /** "-25.0%", "+3.1%" o "=". */
    public static String cambio(long antes, long despues) {
        if (antes == despues) {
            return "=";
        }
        if (antes == 0) {
            return "+" + despues;
        }
        double p = 100.0 * (despues - antes) / antes;
        return String.format(java.util.Locale.ROOT, "%+.1f%%", p);
    }
}
