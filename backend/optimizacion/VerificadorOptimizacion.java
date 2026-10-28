// Archivo: VerificadorOptimizacion.java
//
// Arnes de pruebas de la optimizacion. Tiene su propio main y no pasa por Main.
//
// Uso:
//   java -Dfile.encoding=UTF-8 -cp out backend.optimizacion.VerificadorOptimizacion
//        [-hasta N] [-salida DIR] [-costos archivo.properties] [archivos.easy ...]
//
// Sin archivos toma Tests\*.easy y Tests\optimizacion\*.easy.
//
// Por cada programa SIN errores:
//   1. Ejecuta el codigo sin optimizar en el simulador: esa traza es la
//      referencia.
//   2. Optimiza con F1, luego con F1-F2, ... hasta F'N' (6 por omision) y
//      ejecuta cada version. La traza tiene que ser IDENTICA a la referencia:
//      es la prueba de que la optimizacion no cambio lo que hace el programa.
//      (Si el programa no termina y el simulador lo corta, basta con que una
//      salida sea prefijo de la otra: el codigo optimizado da mas vueltas en
//      los mismos pasos.)
//   3. Mide cada version (MetricasCodigo) y avisa si una fase sube el costo
//      dinamico respecto a la anterior.
//
// Con -salida DIR deja por programa <base>.optimizado.txt (el codigo final),
// <base>.metricas.txt (cada metrica en cada fase) y <base>.bitacora.txt.
//
// Codigo de salida: 1 si alguna traza difiere, hay saltos rotos o el
// optimizador lanzo una excepcion; 0 si no.
package backend.optimizacion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import backend.Cuadruplo;
import backend.EasyCompiler;
import backend.ErrorCompilador;
import backend.GeneradorCodigo;
import backend.ParseException;
import backend.SimpleNode;
import backend.TablaSimbolos;
import backend.TokenMgrError;
import backend.AnalizadorSemantico;

public final class VerificadorOptimizacion {

    private static final String ADVERTENCIA = "Advertencia";

    private static int hasta = AlgoritmosOptimizacion.TODAS_LAS_FASES;
    private static CostosEjecucion costos = new CostosEjecucion();

    /** Lo que se averiguo de un programa, para el resumen. */
    private static final class Resultado {
        String archivo;
        boolean conErrores;
        String fallo;                         // excepcion o saltos rotos
        List<String> trazasDistintas = new ArrayList<String>();
        List<String> avisos = new ArrayList<String>();
        List<MetricasCodigo> porFase = new ArrayList<MetricasCodigo>();   // F0 ... Fn
        boolean cortado;

        boolean fallo() {
            return fallo != null || !trazasDistintas.isEmpty();
        }

        MetricasCodigo inicial() {
            return porFase.get(0);
        }

        MetricasCodigo ultima() {
            return porFase.get(porFase.size() - 1);
        }
    }

    public static void main(String[] args) throws IOException {
        File salida = null;
        List<String> nombres = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            if ("-salida".equals(args[i]) && i + 1 < args.length) {
                salida = new File(args[++i]);
            } else if ("-hasta".equals(args[i]) && i + 1 < args.length) {
                hasta = Integer.parseInt(args[++i].replace("F", "").replace("f", ""));
            } else if ("-costos".equals(args[i]) && i + 1 < args.length) {
                costos = CostosEjecucion.desdeArchivo(args[++i]);
            } else {
                nombres.add(args[i]);
            }
        }
        if (nombres.isEmpty()) {
            nombres.addAll(programasDe(new File("Tests")));
            nombres.addAll(programasDe(new File("Tests", "optimizacion")));
        }
        if (salida != null) {
            Files.createDirectories(salida.toPath());
        }

        List<Resultado> resultados = new ArrayList<Resultado>();
        for (String nombre : nombres) {
            resultados.add(verificar(new File(nombre), salida));
        }
        boolean algunFallo = imprimirResumen(resultados);
        System.exit(algunFallo ? 1 : 0);
    }

    /** Los .easy de una carpeta, en orden alfabetico sin distinguir mayusculas. */
    private static List<String> programasDe(File carpeta) {
        List<String> nombres = new ArrayList<String>();
        File[] encontrados = carpeta.listFiles();
        if (encontrados != null) {
            for (File f : encontrados) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".easy")) {
                    nombres.add(f.getPath());
                }
            }
        }
        nombres.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return nombres;
    }

    // ------------------------------------------------------------------
    // Un programa
    // ------------------------------------------------------------------

    private static Resultado verificar(File fuente, File salida) throws IOException {
        Resultado r = new Resultado();
        r.archivo = fuente.getName();
        if (!fuente.isFile()) {
            r.fallo = "no existe";
            return r;
        }

        TablaSimbolos.reiniciar();
        GeneradorCodigo.reiniciar();
        EasyCompiler.listaErrores.clear();
        EasyCompiler.arbolParcial = false;

        try {
            r.conErrores = !compilar(fuente);
        } catch (Throwable e) {
            r.fallo = "el compilador lanzo " + e;
            return r;
        }
        String base = nombreBase(fuente.getName());
        if (r.conErrores) {
            if (salida != null) {
                List<String> nada = Arrays.asList("no optimizado: el programa tiene errores");
                escribir(new File(salida, base + ".optimizado.txt"), nada);
                escribir(new File(salida, base + ".metricas.txt"), nada);
                escribir(new File(salida, base + ".bitacora.txt"), nada);
            }
            return r;
        }

        List<Cuadruplo> codigo = GeneradorCodigo.obtenerCodigo();
        AlgoritmosOptimizacion.Resultado ultimo = null;
        try {
            AlgoritmosOptimizacion.Resultado cero = AlgoritmosOptimizacion.optimizar(codigo, 0);
            r.porFase.add(cero.metricasOriginal(costos, true));
            for (int k = 1; k <= hasta; k++) {
                ultimo = AlgoritmosOptimizacion.optimizar(codigo, k);
                MetricasCodigo m = ultimo.metricasOptimizado(costos, true);
                MetricasCodigo anterior = r.ultima();
                r.porFase.add(m);
                String comparacion = compararTrazas(r.inicial().traza, m.traza);
                if (comparacion == null) {
                    r.trazasDistintas.add("F" + k);
                } else if (comparacion.equals("prefijo")) {
                    r.cortado = true;
                }
                if (m.costoDinamico > anterior.costoDinamico && !r.cortado) {
                    r.avisos.add("F" + k + " sube el costo dinamico de " + anterior.costoDinamico
                        + " a " + m.costoDinamico);
                }
            }
        } catch (RuntimeException e) {
            r.fallo = "el optimizador lanzo " + e;
            return r;
        }

        if (salida != null) {
            List<String> lineas = new ArrayList<String>();
            List<Cuadruplo> fin = (ultimo == null) ? codigo : ultimo.codigo;
            for (Cuadruplo c : fin) {
                lineas.add(Cuadruplos.linea(c));
            }
            escribir(new File(salida, base + ".optimizado.txt"), lineas);
            escribir(new File(salida, base + ".metricas.txt"), tablaDeMetricas(r));
            escribir(new File(salida, base + ".bitacora.txt"),
                (ultimo == null) ? Arrays.asList("sin fases aplicadas") : ultimo.bitacora());
        }
        return r;
    }

    /** Compila como Main, sin imprimir. Devuelve true si no hubo errores (las advertencias no cuentan). */
    private static boolean compilar(File fuente) throws IOException {
        boolean abortado = false;
        try (InputStream entrada = new FileInputStream(fuente)) {
            EasyCompiler compilador = new EasyCompiler(entrada);
            SimpleNode raiz = null;
            try {
                raiz = compilador.Programa();
            } catch (ParseException e) {
                abortado = true;
            } catch (TokenMgrError e) {
                abortado = true;
            }
            new AnalizadorSemantico().analizar(raiz);
        }
        if (abortado || EasyCompiler.arbolParcial) {
            return false;
        }
        for (ErrorCompilador e : EasyCompiler.listaErrores) {
            if (!ADVERTENCIA.equals(e.tipo)) {
                return false;
            }
        }
        return true;
    }

    /**
     * "igual" si las trazas coinciden, "prefijo" si el simulador corto alguna y
     * la salida de una es prefijo de la otra, null si difieren.
     */
    private static String compararTrazas(List<String> a, List<String> b) {
        if (a.equals(b)) {
            return "igual";
        }
        boolean corteA = a.contains("corte: limite de pasos");
        boolean corteB = b.contains("corte: limite de pasos");
        if (!corteA && !corteB) {
            return null;
        }
        List<String> sa = salidaDe(a);
        List<String> sb = salidaDe(b);
        List<String> corta = sa.size() <= sb.size() ? sa : sb;
        List<String> larga = sa.size() <= sb.size() ? sb : sa;
        return larga.subList(0, corta.size()).equals(corta) ? "prefijo" : null;
    }

    /** Las lineas de la traza antes de la terminacion: lo que imprimio y leyo el programa. */
    private static List<String> salidaDe(List<String> traza) {
        List<String> s = new ArrayList<String>();
        for (String l : traza) {
            if (!l.startsWith("imprimir: ") && !l.startsWith("leer ")) {
                break;
            }
            s.add(l);
        }
        return s;
    }

    private static List<String> tablaDeMetricas(Resultado r) {
        List<String> lineas = new ArrayList<String>();
        StringBuilder cabecera = new StringBuilder(String.format("%-24s", "METRICA"));
        for (int k = 0; k < r.porFase.size(); k++) {
            cabecera.append(String.format(" | %10s", "F" + k));
        }
        lineas.add(cabecera.append(" | CAMBIO").toString());
        List<List<String[]>> columnas = new ArrayList<List<String[]>>();
        for (MetricasCodigo m : r.porFase) {
            columnas.add(MetricasCodigo.comparar(r.inicial(), m));
        }
        for (int fila = 0; fila < columnas.get(0).size(); fila++) {
            StringBuilder sb = new StringBuilder(String.format("%-24s", columnas.get(0).get(fila)[0]));
            for (List<String[]> col : columnas) {
                sb.append(String.format(" | %10s", col.get(fila)[2]));
            }
            sb.append(" | ").append(columnas.get(columnas.size() - 1).get(fila)[3]);
            lineas.add(sb.toString());
        }
        lineas.add("Terminacion: " + r.ultima().terminacion
            + (r.trazasDistintas.isEmpty() ? "   traza identica a la original" : "   TRAZA DISTINTA en " + r.trazasDistintas));
        for (String a : r.avisos) {
            lineas.add("Aviso: " + a);
        }
        return lineas;
    }

    // ------------------------------------------------------------------
    // Resumen
    // ------------------------------------------------------------------

    private static boolean imprimirResumen(List<Resultado> resultados) {
        String formato = "| %-26s | %-11s | %-13s | %-15s | %-9s | %-11s | %-9s |%n";
        char[] guiones = new char[26 + 11 + 13 + 15 + 9 + 11 + 9 + 3 * 6 + 2];
        Arrays.fill(guiones, '-');
        String borde = "+" + new String(guiones) + "+";

        System.out.println("Optimizacion F1-F" + hasta + " (valores: original -> optimizado)");
        System.out.println(borde);
        System.out.printf(formato, "ARCHIVO", "INSTR", "EJECUTADAS", "COSTO DINAMICO", "TEMPORAL", "BYTES TEMP", "TRAZA");
        System.out.println(borde);

        boolean algunFallo = false;
        long[] total = new long[8];
        int optimizados = 0;
        List<String> detalles = new ArrayList<String>();
        for (Resultado r : resultados) {
            if (r.fallo()) {
                algunFallo = true;
            }
            String nombre = recortar(r.archivo, 26);
            if (r.fallo != null) {
                System.out.printf(formato, nombre, "-", "-", "-", "-", "-", "FALLO");
                detalles.add(r.archivo + ": " + r.fallo);
                continue;
            }
            if (r.conErrores) {
                System.out.printf(formato, nombre, "-", "-", "-", "-", "-", "errores");
                continue;
            }
            optimizados++;
            MetricasCodigo a = r.inicial();
            MetricasCodigo b = r.ultima();
            total[0] += a.instrucciones;  total[1] += b.instrucciones;
            total[2] += a.pasos;          total[3] += b.pasos;
            total[4] += a.costoDinamico;  total[5] += b.costoDinamico;
            total[6] += a.bytesTemporales; total[7] += b.bytesTemporales;
            String traza = !r.trazasDistintas.isEmpty() ? "DISTINTA" : (r.cortado ? "prefijo" : "igual");
            System.out.printf(formato, nombre,
                a.instrucciones + "->" + b.instrucciones,
                a.pasos + "->" + b.pasos,
                a.costoDinamico + "->" + b.costoDinamico,
                a.temporales + "->" + b.temporales,
                a.bytesTemporales + "->" + b.bytesTemporales,
                traza);
            if (!r.trazasDistintas.isEmpty()) {
                detalles.add(r.archivo + ": la traza cambia en " + r.trazasDistintas);
            }
            for (String av : r.avisos) {
                detalles.add(r.archivo + ": aviso, " + av);
            }
        }
        System.out.println(borde);
        System.out.println("  Programas: " + resultados.size() + "   optimizados: " + optimizados
            + "   con errores (no se optimizan): " + contarConErrores(resultados));
        System.out.println("  Instrucciones:       " + total[0] + " -> " + total[1] + "  (" + MetricasCodigo.cambio(total[0], total[1]) + ")");
        System.out.println("  Instr. ejecutadas:   " + total[2] + " -> " + total[3] + "  (" + MetricasCodigo.cambio(total[2], total[3]) + ")");
        System.out.println("  Costo dinamico:      " + total[4] + " -> " + total[5] + "  (" + MetricasCodigo.cambio(total[4], total[5]) + ")");
        System.out.println("  Bytes de temporales: " + total[6] + " -> " + total[7] + "  (" + MetricasCodigo.cambio(total[6], total[7]) + ")");
        for (String d : detalles) {
            System.out.println("  " + d);
        }
        System.out.println(algunFallo
            ? "  RESULTADO: HAY FALLOS (trazas distintas, saltos rotos o excepciones)."
            : "  RESULTADO: todas las versiones optimizadas hacen lo mismo que el original.");
        return algunFallo;
    }

    private static int contarConErrores(List<Resultado> resultados) {
        int n = 0;
        for (Resultado r : resultados) {
            if (r.conErrores) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Archivos
    // ------------------------------------------------------------------

    /** UTF-8 y '\n' siempre, para que la comparacion no dependa del equipo. */
    private static void escribir(File archivo, List<String> lineas) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lineas) {
            sb.append(l).append('\n');
        }
        Files.write(archivo.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String nombreBase(String nombre) {
        return nombre.toLowerCase().endsWith(".easy") ? nombre.substring(0, nombre.length() - 5) : nombre;
    }

    private static String recortar(String texto, int ancho) {
        return (texto.length() <= ancho) ? texto : texto.substring(0, ancho - 3) + "...";
    }
}
