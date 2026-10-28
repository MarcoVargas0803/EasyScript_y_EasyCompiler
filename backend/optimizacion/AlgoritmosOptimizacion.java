// Archivo: AlgoritmosOptimizacion.java
//
// Orquestador de la optimizacion del codigo intermedio.
//
// Trabaja sobre una COPIA de los cuadruplos del generador: el codigo sin
// optimizar sigue intacto para --codigo-intermedio y para el arnes de la
// Unidad 2. Aplica las fases en orden (F1 ... F6) y repite la serie completa
// mientras alguna siga cambiando el codigo, porque cada fase abre
// oportunidades a las demas (un plegado deja una copia que F2 aprovecha, una
// redundancia eliminada deja una asignacion muerta para F6...).
//
// Al terminar comprueba que ningun salto apunte a una etiqueta que ya no
// existe: si pasara, es un fallo del optimizador y se lanza una excepcion para
// que quien llama se quede con el codigo sin optimizar.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import backend.Cuadruplo;
import backend.ErrorCompilador;
import backend.TablaSimbolos;
import backend.Tipo;

public final class AlgoritmosOptimizacion {

    /** Tope de vueltas de la serie completa, por si dos fases se deshicieran mutuamente. */
    private static final int MAXIMO_DE_VUELTAS = 10;

    public static final int TODAS_LAS_FASES = 6;

    private AlgoritmosOptimizacion() {
    }

    /** Las fases en el orden en que se aplican. */
    static List<Fase> fases() {
        List<Fase> f = new ArrayList<Fase>();
        f.add(new PlegadoConstantes());
        f.add(new EliminacionRedundancias());
        f.add(new ReordenacionOperaciones());
        f.add(new OptimizacionBucles());
        f.add(new OptimizacionRegiones());
        f.add(new AsignacionesMuertas());
        return f;
    }

    /** Lo que deja la optimizacion. */
    public static final class Resultado {
        public final List<Cuadruplo> original;
        public final List<Cuadruplo> codigo;
        public final List<ErrorCompilador> advertencias;
        final ProgramaIntermedio programa;
        final Bitacora bitacora;
        final int fasesAplicadas;
        int vueltas;

        Resultado(List<Cuadruplo> original, ProgramaIntermedio programa, Bitacora bitacora, int fasesAplicadas) {
            this.original = original;
            this.codigo = programa.codigo;
            this.advertencias = programa.advertencias;
            this.programa = programa;
            this.bitacora = bitacora;
            this.fasesAplicadas = fasesAplicadas;
        }

        /** Metricas del codigo sin optimizar. */
        public MetricasCodigo metricasOriginal(CostosEjecucion costos, boolean simular) {
            return MetricasCodigo.medir(original, programa.tipos, TablaSimbolos.tamanoDelMarco(), costos, simular);
        }

        /** Metricas del codigo optimizado. */
        public MetricasCodigo metricasOptimizado(CostosEjecucion costos, boolean simular) {
            return MetricasCodigo.medir(codigo, programa.tipos, TablaSimbolos.tamanoDelMarco(), costos, simular);
        }

        /**
         * Temporales que quedaron en el codigo optimizado, con su tipo, ancho y
         * direccion: {nombre, tipo, ancho, direccion}. Las direcciones se
         * reparten igual que GeneradorCodigo.asignarDireccionesTemporales: uno
         * tras otro a partir del final de los datos.
         */
        public List<String[]> temporales() {
            List<Integer> numeros = new ArrayList<Integer>();
            for (Cuadruplo c : codigo) {
                for (String s : new String[] { c.arg1, c.arg2, c.resultado }) {
                    if (Cuadruplos.esTemporal(s)) {
                        Integer n = Integer.valueOf(s.substring(1));
                        if (!numeros.contains(n)) {
                            numeros.add(n);
                        }
                    }
                }
            }
            Collections.sort(numeros);
            List<String[]> filas = new ArrayList<String[]>();
            int direccion = TablaSimbolos.tamanoDelMarco();
            for (Integer n : numeros) {
                String nombre = "t" + n;
                String tipo = programa.tipoDe(nombre);
                int ancho = (tipo == null) ? 0 : Tipo.desdeLexema(tipo).ancho;
                filas.add(new String[] { nombre, String.valueOf(tipo), String.valueOf(ancho), String.valueOf(direccion) });
                direccion += ancho;
            }
            return filas;
        }

        /** El grafo de flujo y cada transformacion, por fase. */
        public List<String> bitacora() {
            List<String> lineas = new ArrayList<String>();
            lineas.add("Fases aplicadas: F1-F" + fasesAplicadas + ", vueltas de la serie completa: " + vueltas);
            for (Fase f : fases().subList(0, fasesAplicadas)) {
                lineas.add("");
                lineas.add(f.clave() + " - " + f.nombre() + ": " + bitacora.cantidad(f.clave()) + " transformaciones");
                for (Bitacora.Entrada e : bitacora.entradas()) {
                    if (e.fase.equals(f.clave())) {
                        lineas.add("  " + e.regla + ": " + e.antes + "  ->  " + e.despues);
                    }
                }
            }
            lineas.add("");
            lineas.add("Grafo de flujo del codigo optimizado");
            lineas.addAll(new GrafoFlujo(codigo, Collections.<String>emptySet()).describir());
            return lineas;
        }
    }

    /** Optimiza con todas las fases. */
    public static Resultado optimizar(List<Cuadruplo> original) {
        return optimizar(original, TODAS_LAS_FASES);
    }

    /**
     * Optimiza aplicando solo las fases F1 ... F'hasta'. Con hasta = 0 devuelve
     * una copia sin cambios (la referencia contra la que se mide).
     */
    public static Resultado optimizar(List<Cuadruplo> original, int hasta) {
        ProgramaIntermedio programa = new ProgramaIntermedio(original);
        Bitacora bitacora = new Bitacora();
        List<Fase> activas = fases().subList(0, Math.max(0, Math.min(hasta, TODAS_LAS_FASES)));

        Resultado r = new Resultado(original, programa, bitacora, activas.size());
        boolean cambio = !activas.isEmpty();
        while (cambio && r.vueltas < MAXIMO_DE_VUELTAS) {
            cambio = false;
            r.vueltas++;
            for (Fase f : activas) {
                if (f.aplicar(programa, bitacora)) {
                    cambio = true;
                }
                Cuadruplos.renumerar(programa.codigo);
            }
        }

        List<String> rotas = Cuadruplos.etiquetasRotas(programa.codigo);
        if (!rotas.isEmpty()) {
            throw new IllegalStateException("la optimizacion dejo saltos a etiquetas inexistentes: " + rotas);
        }
        return r;
    }
}
