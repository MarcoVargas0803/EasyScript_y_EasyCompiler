// Archivo: VerificadorIntermedio.java
//
// Arnes de pruebas del codigo intermedio (Unidad 2). Tiene su propio main y no
// pasa por Main, de modo que la consola del compilador no cambia en nada.
//
// Uso:
//   java -Dfile.encoding=UTF-8 -cp out VerificadorIntermedio [-salida DIR] [archivos.txt ...]
//
// Sin archivos analiza todos los *.txt del directorio actual, en orden
// alfabetico.
//
// Que comprueba por cada archivo
// ------------------------------
//   1. Lo compila igual que Main (parser + AnalizadorSemantico), sin imprimir
//      el arbol ni las tablas, y cuenta los errores por tipo.
//   2. Que ningun salto apunte a una etiqueta inexistente ni se haya quedado
//      con el destino pendiente ("_"). Eso seria un fallo del generador, no
//      del programa del usuario.
//   3. La FORMA del codigo (Aho, seccion 6.6.5): cuenta los saltos que un
//      generador bien construido no deberia emitir. No es un fallo, porque el
//      codigo sigue siendo correcto, pero se informa para que no reaparezcan.
//   4. Si el programa no tiene errores (las advertencias no cuentan), ejecuta
//      el codigo con SimuladorCuadruplos. Con errores no tiene sentido: el
//      generador se salta las partes con tipo ERROR y el codigo queda parcial.
//
// Con -salida DIR deja por cada archivo <base>.cuadruplos.txt y
// <base>.traza.txt, que son los que pruebas_u2.ps1 compara entre etapas.
//
// Codigo de salida: 1 si algun archivo tiene saltos rotos o pendientes (o no
// se pudo analizar), 0 si no.
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

public class VerificadorIntermedio {

    // Los tipos de error se escriben con acento en el resto del compilador.
    // Aqui van con escapes Unicode para que este archivo no dependa de la
    // codificacion con que se abra.
    private static final String LEXICO = "Léxico";
    private static final String SINTACTICO = "Sintáctico";
    private static final String SEMANTICO = "Semántico";
    private static final String ADVERTENCIA = "Advertencia";

    private static final String NO_SIMULADO = "no simulado: codigo parcial";

    /** Lo que se averiguo de un archivo, para el resumen. */
    private static class Resultado {
        String archivo;
        boolean existe = true;
        String excepcion = null;     // fallo inesperado del compilador
        int instrucciones;
        int lexicos, sintacticos, semanticos, advertencias;
        boolean abortado;            // el parser lanzo ParseException hasta arriba
        List<String> saltosRotos = new ArrayList<String>();
        boolean simulado;
        String terminacion;
        int pasos;
        Forma forma;

        boolean fallo() {
            return !existe || excepcion != null || !saltosRotos.isEmpty();
        }
    }

    /**
     * Saltos y etiquetas que sobran. Ninguno cambia lo que hace el programa;
     * solo lo alargan.
     *
     *   saltoASiguiente  "ir_a L" seguido (solo con etiquetas en medio) de "L:"
     *   saltoSobreSalto  "si x ir_a L1 ; ir_a L2 ; L1:": bastaba con invertir
     *                    la condicion y saltar a L2
     *   etiquetaSinUso   una etiqueta a la que no salta nadie
     *   etiquetasSeguidas dos etiquetas juntas, que marcan el mismo sitio
     */
    static class Forma {
        int saltoASiguiente, saltoSobreSalto, etiquetaSinUso, etiquetasSeguidas;

        int total() {
            return saltoASiguiente + saltoSobreSalto + etiquetaSinUso + etiquetasSeguidas;
        }

        static Forma de(List<Cuadruplo> codigo) {
            Forma f = new Forma();
            List<String> usadas = new ArrayList<String>();
            for (Cuadruplo c : codigo) {
                if (esSalto(c)) {
                    usadas.add(c.resultado);
                }
            }
            for (int i = 0; i < codigo.size(); i++) {
                Cuadruplo c = codigo.get(i);
                if ("etiqueta".equals(c.operador)) {
                    if (!usadas.contains(c.resultado)) {
                        f.etiquetaSinUso++;
                    }
                    if (i + 1 < codigo.size() && "etiqueta".equals(codigo.get(i + 1).operador)) {
                        f.etiquetasSeguidas++;
                    }
                    continue;
                }
                if ("ir_a".equals(c.operador) && caeEn(codigo, i + 1, c.resultado)) {
                    f.saltoASiguiente++;
                }
                if (c.operador.startsWith("si_") && i + 1 < codigo.size()
                        && "ir_a".equals(codigo.get(i + 1).operador)
                        && caeEn(codigo, i + 2, c.resultado)) {
                    f.saltoSobreSalto++;
                }
            }
            return f;
        }

        /** true si desde la posicion i, pasando solo por etiquetas, se llega a "etiqueta:". */
        private static boolean caeEn(List<Cuadruplo> codigo, int i, String etiqueta) {
            for (int k = i; k < codigo.size() && "etiqueta".equals(codigo.get(k).operador); k++) {
                if (codigo.get(k).resultado.equals(etiqueta)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean esSalto(Cuadruplo c) {
            return "ir_a".equals(c.operador) || c.operador.startsWith("si_");
        }

        public String toString() {
            return saltoASiguiente + " ir_a a la siguiente, " + saltoSobreSalto + " si sobre un ir_a, "
                + etiquetaSinUso + " etiquetas sin uso, " + etiquetasSeguidas + " etiquetas seguidas";
        }
    }

    public static void main(String[] args) throws IOException {
        File salida = null;
        List<String> nombres = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            if ("-salida".equals(args[i]) && i + 1 < args.length) {
                salida = new File(args[++i]);
            } else {
                nombres.add(args[i]);
            }
        }

        if (nombres.isEmpty()) {
            File[] encontrados = new File(".").listFiles();
            if (encontrados != null) {
                for (File f : encontrados) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".txt")) {
                        nombres.add(f.getName());
                    }
                }
            }
            // Alfabetico sin distinguir mayusculas, que es como lo lista el
            // explorador de Windows y Get-ChildItem. El desempate exacto deja
            // el orden totalmente determinado.
            nombres.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        }

        if (salida != null) {
            Files.createDirectories(salida.toPath());
        }

        List<Resultado> resultados = new ArrayList<Resultado>();
        for (String nombre : nombres) {
            resultados.add(verificar(nombre, salida));
        }

        boolean algunFallo = imprimirResumen(resultados);
        System.exit(algunFallo ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // Un archivo
    // ------------------------------------------------------------------

    private static Resultado verificar(String nombre, File salida) throws IOException {
        Resultado r = new Resultado();
        r.archivo = nombre;

        File fuente = new File(nombre);
        if (!fuente.isFile()) {
            r.existe = false;
            return r;
        }

        // Todo el estado del compilador es estatico: sin esto el segundo
        // archivo heredaria los simbolos, los cuadruplos y los errores del
        // primero. Revisado el codigo, estos cuatro son todo el estado mutable
        // (CuboSemantico e ImpresorArbol solo tienen tablas fijas, y el parser
        // y el analizador se crean nuevos en cada vuelta).
        TablaSimbolos.reiniciar();
        GeneradorCodigo.reiniciar();
        EasyCompiler.listaErrores.clear();
        EasyCompiler.arbolParcial = false;

        try {
            compilar(fuente, r);
        } catch (Throwable e) {
            // Main no protege contra esto; aqui se atrapa para que un archivo
            // problematico no impida verificar los demas.
            r.excepcion = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        List<Cuadruplo> codigo = GeneradorCodigo.obtenerCodigo();
        r.instrucciones = codigo.size();
        r.forma = Forma.de(codigo);

        r.saltosRotos.addAll(GeneradorCodigo.etiquetasRotas());
        for (Cuadruplo c : codigo) {
            boolean esSalto = "ir_a".equals(c.operador) || c.operador.startsWith("si_");
            if (esSalto && GeneradorCodigo.PENDIENTE.equals(c.resultado)) {
                String marca = "pendiente en #" + c.indice;
                if (!r.saltosRotos.contains(marca)) {
                    r.saltosRotos.add(marca);
                }
            }
        }

        boolean hayErrores = r.lexicos > 0 || r.sintacticos > 0 || r.semanticos > 0
            || r.abortado || EasyCompiler.arbolParcial || r.excepcion != null;

        List<String> traza;
        if (hayErrores) {
            traza = new ArrayList<String>();
            traza.add(NO_SIMULADO);
        } else {
            SimuladorCuadruplos sim = new SimuladorCuadruplos(codigo);
            traza = sim.ejecutar();
            r.simulado = true;
            r.terminacion = sim.getTerminacion();
            r.pasos = sim.getPasos();
        }

        if (salida != null) {
            String base = nombreBase(fuente.getName());
            escribir(new File(salida, base + ".cuadruplos.txt"), lineasDeCuadruplos(codigo));
            escribir(new File(salida, base + ".traza.txt"), traza);
        }
        return r;
    }

    /** Los mismos pasos que Main, sin imprimir nada. */
    private static void compilar(File fuente, Resultado r) throws IOException {
        try (InputStream entrada = new FileInputStream(fuente)) {
            EasyCompiler compilador = new EasyCompiler(entrada);
            SimpleNode raiz = null;
            try {
                raiz = compilador.Programa();
            } catch (ParseException e) {
                // Main solo lo imprime, no lo agrega a la lista. Aqui se cuenta
                // como un error sintactico mas, porque el codigo es parcial.
                r.abortado = true;
            } catch (TokenMgrError e) {
                EasyCompiler.listaErrores.add(new ErrorCompilador(LEXICO, -1, -1,
                    "Error fatal del escáner: " + e.getMessage(),
                    "El archivo contiene caracteres inválidos o estructuras léxicas irreparables."));
            }
            new AnalizadorSemantico().analizar(raiz);
        } finally {
            contarErrores(r);
        }
    }

    private static void contarErrores(Resultado r) {
        for (ErrorCompilador e : EasyCompiler.listaErrores) {
            if (LEXICO.equals(e.tipo)) {
                r.lexicos++;
            } else if (SINTACTICO.equals(e.tipo)) {
                r.sintacticos++;
            } else if (ADVERTENCIA.equals(e.tipo)) {
                r.advertencias++;
            } else if (SEMANTICO.equals(e.tipo)) {
                r.semanticos++;
            } else {
                // Un tipo que no se reconoce se cuenta como error semantico: es
                // lo prudente, porque impide simular codigo dudoso.
                r.semanticos++;
            }
        }
        if (r.abortado) {
            r.sintacticos++;
        }
    }

    // ------------------------------------------------------------------
    // Archivos de salida
    // ------------------------------------------------------------------

    private static List<String> lineasDeCuadruplos(List<Cuadruplo> codigo) {
        List<String> lineas = new ArrayList<String>();
        for (Cuadruplo c : codigo) {
            lineas.add(c.indice + " | " + c.operador + " | " + c.arg1 + " | " + c.arg2
                + " | " + c.resultado + " | " + c.comentario);
        }
        return lineas;
    }

    /** UTF-8 y '\n' siempre, para que la comparacion no dependa del equipo. */
    private static void escribir(File archivo, List<String> lineas) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lineas) {
            sb.append(l).append('\n');
        }
        Files.write(archivo.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String nombreBase(String nombre) {
        if (nombre.toLowerCase().endsWith(".txt")) {
            return nombre.substring(0, nombre.length() - 4);
        }
        return nombre;
    }

    // ------------------------------------------------------------------
    // Resumen
    // ------------------------------------------------------------------

    /** Imprime la tabla y devuelve true si algun archivo fallo. */
    private static boolean imprimirResumen(List<Resultado> resultados) {
        String formato = "| %-26s | %5s | %3s | %3s | %3s | %3s | %-9s | %-30s |%n";
        // Ancho de las 8 columnas mas los separadores " | " que hay entre ellas
        // y el espacio de cada borde: asi el "+---+" mide lo mismo que una fila.
        char[] guiones = new char[(26 + 5 + 3 * 4 + 9 + 30) + 3 * 7 + 2];
        Arrays.fill(guiones, '-');
        String borde = "+" + new String(guiones) + "+";

        System.out.println(borde);
        System.out.printf(formato, "ARCHIVO", "INSTR", "LEX", "SIN", "SEM", "ADV", "SALTOS", "SIMULACION");
        System.out.println(borde);

        boolean algunFallo = false;
        int simulados = 0;
        int parciales = 0;
        int instrucciones = 0;
        Forma forma = new Forma();
        List<String> detalles = new ArrayList<String>();
        List<String> detallesDeForma = new ArrayList<String>();

        for (Resultado r : resultados) {
            if (r.fallo()) {
                algunFallo = true;
            }
            if (!r.existe) {
                System.out.printf(formato, recortar(r.archivo, 26), "-", "-", "-", "-", "-", "-", "NO EXISTE");
                continue;
            }
            String saltos = r.saltosRotos.isEmpty() ? "ok" : "ROTOS";
            instrucciones += r.instrucciones;
            forma.saltoASiguiente += r.forma.saltoASiguiente;
            forma.saltoSobreSalto += r.forma.saltoSobreSalto;
            forma.etiquetaSinUso += r.forma.etiquetaSinUso;
            forma.etiquetasSeguidas += r.forma.etiquetasSeguidas;
            if (r.forma.total() > 0) {
                detallesDeForma.add(r.archivo + ": " + r.forma);
            }
            String sim;
            if (r.excepcion != null) {
                sim = "EXCEPCION";
                detalles.add(r.archivo + ": " + r.excepcion);
            } else if (r.simulado) {
                simulados++;
                sim = abreviar(r.terminacion) + ", " + r.pasos + " pasos";
            } else {
                parciales++;
                sim = "parcial";
            }
            if (!r.saltosRotos.isEmpty()) {
                detalles.add(r.archivo + ": saltos rotos " + r.saltosRotos);
            }
            System.out.printf(formato, recortar(r.archivo, 26), r.instrucciones,
                r.lexicos, r.sintacticos + (r.abortado ? "*" : ""), r.semanticos, r.advertencias,
                saltos, recortar(sim, 30));
        }
        System.out.println(borde);
        System.out.println("  Archivos: " + resultados.size() + "   simulados: " + simulados
            + "   parciales (con errores): " + parciales);
        System.out.println("  Instrucciones en total: " + instrucciones);
        System.out.println("  Forma del codigo: " + forma);
        for (String d : detallesDeForma) {
            System.out.println("    " + d);
        }
        if (resultados.stream().anyMatch(r -> r.abortado)) {
            System.out.println("  * incluye una ParseException que llego hasta Main (analisis abortado).");
        }
        for (String d : detalles) {
            System.out.println("  " + d);
        }
        System.out.println(algunFallo
            ? "  RESULTADO: HAY FALLOS (saltos rotos, archivos inexistentes o excepciones)."
            : "  RESULTADO: todos los saltos tienen destino.");
        return algunFallo;
    }

    private static String abreviar(String terminacion) {
        if (terminacion == null) {
            return "?";
        }
        if (terminacion.startsWith("corte")) {
            return "CORTE";
        }
        if (terminacion.startsWith("error")) {
            return "ERROR EJEC";
        }
        if (terminacion.startsWith("operador")) {
            return "OP DESCONOCIDO";
        }
        return terminacion;
    }

    private static String recortar(String texto, int ancho) {
        if (texto.length() <= ancho) {
            return texto;
        }
        return texto.substring(0, ancho - 3) + "...";
    }
}
