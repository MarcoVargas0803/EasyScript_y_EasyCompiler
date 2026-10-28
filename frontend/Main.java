package frontend;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import backend.*;
import backend.optimizacion.AlgoritmosOptimizacion;
import backend.optimizacion.CostosEjecucion;
import backend.optimizacion.MetricasCodigo;

public class Main {
    // Códigos ANSI para colores en la terminal
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREEN = "\u001B[32m";

    public static void main(String args[]) {
        try {
            // Verificamos que al ejecutar envíen el nombre del archivo
            if (args.length == 0) {
                System.out.println("Error: Debes especificar un archivo. Ejemplo: easy Tests\\codigo1.easy");
                System.out.println("Sin opciones solo se muestran los errores y advertencias.");
                System.out.println("Opciones: --arbol-sintactico           Muestra el arbol sintactico (colapsado).");
                System.out.println("          --arbol-sintactico-completo  Muestra el arbol con la derivacion literal,");
                System.out.println("                                       sin colapsar los niveles de las expresiones.");
                System.out.println("          --tabla-simbolos             Muestra la tabla de simbolos.");
                System.out.println("          --codigo-intermedio          Muestra el codigo de tres direcciones que");
                System.out.println("                                       genera el esquema de traduccion.");
                System.out.println("          --codigo-optimizado          Muestra el codigo intermedio optimizado");
                System.out.println("                                       y sus metricas antes y despues.");
                System.out.println("          --bitacora-optimizacion      Muestra cada transformacion del optimizador.");
                sugerirAyuda();
                return;
            }

            // --ayuda sola (easy --ayuda) muestra la sintaxis y las banderas;
            // despues de un archivo (easy codigo1.easy --ayuda) solo las banderas.
            // En ambos casos no se analiza nada.
            if (args[0].equals("--ayuda")) {
                imprimirAyuda();
                return;
            }
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("--ayuda")) {
                    imprimirTablaBanderas();
                    return;
                }
            }

            // Una bandera en el lugar del archivo (easy --tabla-simbolos): falta
            // el archivo, o la bandera se escribio antes que el.
            if (args[0].startsWith("-")) {
                System.out.println("Error: Falta el archivo. Las banderas van despues del archivo, por ejemplo:");
                System.out.println("  easy Tests\\codigo1.easy " + args[0]);
                sugerirAyuda();
                return;
            }

            // Los programas de EasyScript llevan la extension propia .easy, igual
            // que javac solo acepta .java: cualquier otro archivo se rechaza antes
            // de abrirlo.
            if (!args[0].toLowerCase().endsWith(".easy")) {
                System.out.println("Error: El archivo " + args[0] + " no es un programa de EasyScript.");
                System.out.println("Los programas deben tener la extension .easy (por ejemplo, codigo1.easy).");
                sugerirAyuda();
                return;
            }

            // Las banderas pueden venir en cualquier posición después del
            // archivo, así que args[0] sigue siendo el nombre del archivo.
            //
            // El arbol, la tabla y el codigo intermedio se construyen SIEMPRE
            // (el analisis semantico los necesita), pero solo se imprimen si se
            // piden. Por defecto la consola muestra unicamente los errores.
            boolean mostrarArbol = false;
            boolean arbolCompleto = false;
            boolean mostrarTabla = false;
            boolean mostrarCodigo = false;
            boolean mostrarOptimizado = false;
            boolean mostrarBitacora = false;
            boolean banderaDesconocida = false;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--arbol-sintactico")) {
                    mostrarArbol = true;
                } else if (arg.equals("--arbol-sintactico-completo")) {
                    mostrarArbol = true;
                    arbolCompleto = true;
                } else if (arg.equals("--tabla-simbolos")) {
                    mostrarTabla = true;
                } else if (arg.equals("--codigo-intermedio")) {
                    mostrarCodigo = true;
                } else if (arg.equals("--codigo-optimizado")) {
                    mostrarOptimizado = true;
                } else if (arg.equals("--bitacora-optimizacion")) {
                    mostrarBitacora = true;
                } else {
                    // Tambien lo que no empieza con "--" (-tabla, tabla-simbolos,
                    // un segundo archivo): antes se ignoraba sin avisar.
                    System.out.println(ANSI_YELLOW + "Bandera desconocida: " + arg + " (se ignora)" + ANSI_RESET);
                    banderaDesconocida = true;
                }
            }
            // Una sola vez aunque haya varias; el analisis sigue con las demas.
            if (banderaDesconocida) {
                sugerirAyuda();
            }

            // El estado de la tabla de simbolos es estatico: se limpia antes de
            // empezar para que la numeracion de bloques arranque siempre en cero.
            TablaSimbolos.reiniciar();
            GeneradorCodigo.reiniciar();

            // Abrimos el archivo de texto
            FileInputStream archivo = new FileInputStream(args[0]);
            EasyCompiler compilador = new EasyCompiler(archivo);

            // Raíz del árbol sintáctico que construye JJTree durante el análisis.
            SimpleNode raiz = null;

            try {
                raiz = compilador.Programa();
                System.out.println(">> Analisis completado.");
            } catch (ParseException e) {
                System.out.println("Error critico de sintaxis no recuperable: " + e.getMessage());
            } catch (TokenMgrError e) {
                EasyCompiler.listaErrores.add(new ErrorCompilador("Léxico", -1, -1,
                        "Error fatal del escáner: " + e.getMessage(),
                        "El archivo contiene caracteres inválidos o estructuras léxicas irreparables."));
                System.out.println(">> Analisis interrumpido por error léxico fatal.");
            }

            // ---------------------------------------------------------------
            // FASE SEMANTICA
            //
            // Va aqui, entre el parseo y la impresion, por dos razones: llena la
            // tabla de simbolos (que se imprime con --tabla-simbolos), y sus
            // errores tienen que entrar en listaErrores antes del reporte final.
            // ---------------------------------------------------------------
            new AnalizadorSemantico().analizar(raiz);

            // ---------------------------------------------------------------
            // OPTIMIZACION
            //
            // Corre siempre que el programa no tenga errores (con errores el
            // codigo intermedio es parcial). Trabaja sobre una copia: lo que
            // muestra --codigo-intermedio sigue siendo el codigo sin optimizar.
            // Sus advertencias (ej. una division entre cero que se descubre al
            // calcular constantes) se suman al reporte. Si el optimizador
            // fallara, se sigue con el codigo sin optimizar.
            // ---------------------------------------------------------------
            AlgoritmosOptimizacion.Resultado optimizado = null;
            String motivoSinOptimizar = null;
            if (raiz == null || EasyCompiler.arbolParcial || hayErrores()) {
                motivoSinOptimizar = "el programa tiene errores y su codigo intermedio es parcial";
            } else {
                try {
                    optimizado = AlgoritmosOptimizacion.optimizar(GeneradorCodigo.obtenerCodigo());
                    EasyCompiler.listaErrores.addAll(optimizado.advertencias);
                } catch (RuntimeException e) {
                    motivoSinOptimizar = "fallo interno del optimizador (" + e.getMessage()
                            + "); se conserva el codigo sin optimizar";
                }
            }

            if (mostrarArbol) {
                imprimirArbol(raiz, arbolCompleto);
            }

            if (mostrarTabla) {
                imprimirTablaSimbolos();
            }

            if (mostrarCodigo) {
                imprimirCodigoIntermedio();
            }

            if (mostrarOptimizado) {
                imprimirCodigoOptimizado(optimizado, motivoSinOptimizar);
            }

            if (mostrarBitacora) {
                imprimirBitacoraOptimizacion(optimizado, motivoSinOptimizar);
            }

            ordenarErrores();

            // Las advertencias se cuentan aparte de los errores: senalan codigo
            // sospechoso (una variable declarada y nunca usada, una que se lee
            // antes de tener valor), no codigo invalido. Sumarlas al mismo total
            // haria parecer roto un programa que en realidad compila bien.
            int totalErrores = 0;
            int totalAdvertencias = 0;
            for (int i = 0; i < EasyCompiler.listaErrores.size(); i++) {
                if ("Advertencia".equals(EasyCompiler.listaErrores.get(i).tipo)) {
                    totalAdvertencias++;
                } else {
                    totalErrores++;
                }
            }
            String color = (totalErrores > 0) ? ANSI_RED : ANSI_YELLOW;

            if (EasyCompiler.listaErrores.isEmpty()) {
                System.out.println(
                        ANSI_GREEN + ">> ¡Excelente! No se encontraron errores en el código fuente." + ANSI_RESET);
            } else {

                String resumen;
                if (totalErrores > 0 && totalAdvertencias > 0) {
                    resumen = " SE ENCONTRARON " + totalErrores + " ERRORES Y "
                            + totalAdvertencias + " ADVERTENCIA(S)";
                } else if (totalErrores > 0) {
                    resumen = " SE ENCONTRARON " + totalErrores + " ERRORES DURANTE LA COMPILACION";
                } else {
                    resumen = " EL CODIGO COMPILA, CON " + totalAdvertencias + " ADVERTENCIA(S)";
                }
                System.out.println(color + "\n============================================================");
                System.out.println(resumen);
                System.out.println("============================================================" + ANSI_RESET);

                // Recorremos la lista e imprimimos cada error en formato de "Tarjeta"
                for (int i = 0; i < EasyCompiler.listaErrores.size(); i++) {
                    ErrorCompilador error = EasyCompiler.listaErrores.get(i);

                    // Fila 1: Encabezado del error con su ubicación
                    // El codigo (SEM-04, ADV-01...) identifica la regla que salto,
                    // sin depender del texto del mensaje.
                    String etiquetaTipo = error.tipo;
                    if (error.codigo != null && !"-".equals(error.codigo)) {
                        etiquetaTipo = error.tipo + " " + error.codigo;
                    }
                    System.out.printf(
                            ANSI_BLUE + "\n[ Error %d ] --- Tipo: %s | Línea: %d | Columna: %d \n" + ANSI_RESET,
                            (i + 1), etiquetaTipo, error.linea, error.columna);

                    // Fila 2 y 3: Detalle y Consejo
                    System.out.println(ANSI_RED + "  ❌ Detalle : " + error.detalle + ANSI_RESET);
                    System.out.println("  💡 Consejo : " + error.consejo + ANSI_RESET);
                }

                System.out
                        .println(color + "\n============================================================" + ANSI_RESET);

                /*
                 * // Dibujamos la cabecera de la tabla
                 * System.out.println("Total de errores detectados: " +
                 * EasyCompiler.listaErrores.size());
                 * System.out.println(
                 * "==================================================== TABLA DE ERRORES =================================================================================="
                 * );
                 * System.out.printf("| %-15s | %-7s | %-9s | %-45s | %-60s |\n", "TIPO",
                 * "LINEA", "COLUMNA",
                 * "DETALLE DEL ERROR", "CONSEJO");
                 * System.out.println(
                 * "--------------------------------------------------------------------------------------------------------------------------------------------------------"
                 * );
                 * 
                 * // Recorremos la lista e imprimimos cada error con formato
                 * for (ErrorCompilador error : EasyCompiler.listaErrores) {
                 * System.out.printf("| %-15s | %-7d | %-9d | %-45s | %-60s |\n", error.tipo,
                 * error.linea,
                 * error.columna, error.detalle, error.consejo);
                 * }
                 * 
                 * System.out.println(
                 * "========================================================================================================================================================"
                 * );
                 */
            }

            if (!mostrarArbol && !mostrarTabla && !mostrarCodigo && !mostrarOptimizado && !mostrarBitacora) {
                System.out.println(ANSI_BLUE
                        + "  (usa --arbol-sintactico, --tabla-simbolos o --codigo-intermedio para ver mas detalle)"
                        + ANSI_RESET);
            }
        } catch (FileNotFoundException e) {
            System.out.println("El archivo " + args[0] + " no existe en la carpeta.");
            sugerirAyuda();
        }
    }

    /** Va despues de cada error de uso: archivo o bandera mal escritos. */
    private static void sugerirAyuda() {
        System.out.println(ANSI_BLUE + "Para recibir ayuda utiliza el comando 'easy --ayuda'." + ANSI_RESET);
    }

    /** easy --ayuda: la forma de invocar el compilador y sus banderas. */
    private static void imprimirAyuda() {
        System.out.println(ANSI_BLUE + "============================================================");
        System.out.println(" EASYCOMPILER - AYUDA");
        System.out.println("============================================================" + ANSI_RESET);
        System.out.println("Uso:");
        System.out.println("  easy <archivo.easy> [banderas ...]");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  easy Tests\\codigo1.easy");
        System.out.println("  easy Tests\\codigo1.easy --arbol-sintactico --tabla-simbolos");
        System.out.println("  easy Tests\\codigo1.easy --ayuda      (solo la tabla de banderas)");
        System.out.println();
        System.out.println("El archivo debe tener la extension .easy. Las banderas van despues del");
        System.out.println("archivo, en cualquier orden, y se pueden combinar.");
        System.out.println();
        imprimirTablaBanderas();
    }

    /**
     * easy <archivo> --ayuda: las banderas disponibles. Es la misma tabla que
     * aparece al inicio del README.md; si se agrega una bandera, va en los dos.
     */
    private static void imprimirTablaBanderas() {
        String borde = "+-----------------------------+--------------------------------------------------+";
        String formato = "| %-27s | %-48s |%n";
        System.out.println(borde);
        System.out.printf(formato, "BANDERA", "MUESTRA");
        System.out.println(borde);
        System.out.printf(formato, "(ninguna)", "solo errores y advertencias");
        System.out.printf(formato, "--arbol-sintactico", "el árbol sintáctico, colapsado");
        System.out.printf(formato, "--arbol-sintactico-completo", "el árbol con la derivación literal, sin colapsar");
        System.out.printf(formato, "--tabla-simbolos", "la tabla de símbolos");
        System.out.printf(formato, "--codigo-intermedio", "los cuádruplos y la dirección de cada temporal");
        System.out.printf(formato, "--codigo-optimizado", "los cuádruplos optimizados y sus métricas");
        System.out.printf(formato, "--bitacora-optimizacion", "cada transformación del optimizador, por fase");
        System.out.println(borde);
    }

    /**
     * Imprime el codigo de tres direcciones que genero el esquema de traduccion.
     *
     * Solo se llama con la bandera --codigo-intermedio. La salida normal del
     * compilador no lo incluye: por defecto solo se muestran los errores.
     * Ver generacion_ci.md.
     *
     * Se muestran las dos formas: el cuadruplo con sus cuatro casillas, que es
     * la estructura real, y la instruccion escrita como se leeria, que es lo
     * unico que se entiende de un vistazo.
     */
    private static void imprimirCodigoIntermedio() {
        System.out.println(ANSI_BLUE + "\n============================================================");
        System.out.println(" CODIGO INTERMEDIO - CUADRUPLOS");
        System.out.println("============================================================" + ANSI_RESET);

        java.util.List<Cuadruplo> codigo = GeneradorCodigo.obtenerCodigo();
        if (codigo.isEmpty()) {
            System.out.println(ANSI_YELLOW + "  (vacio) No se genero ninguna instruccion." + ANSI_RESET);
            return;
        }

        imprimirCuadruplos(codigo);

        java.util.List<String> rotas = GeneradorCodigo.etiquetasRotas();
        if (!rotas.isEmpty()) {
            System.out.println(ANSI_RED + "  ERROR INTERNO: hay saltos a etiquetas que no existen: "
                    + rotas + ANSI_RESET);
        }

        imprimirTemporales();
    }

    /** La tabla de cuadruplos con su total: la comparten el codigo intermedio y el optimizado. */
    private static void imprimirCuadruplos(java.util.List<Cuadruplo> codigo) {
        String borde = "+-------+------------+------------+------------+------------+";
        System.out.println(borde);
        System.out.printf("| %-5s | %-10s | %-10s | %-10s | %-10s |   %s%n",
                "#", "OPERADOR", "ARG1", "ARG2", "RESULTADO", "INSTRUCCION");
        System.out.println(borde);
        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            System.out.printf("| %-5d | %-10s | %-10s | %-10s | %-10s |   %s%n",
                    c.indice,
                    recortar(c.operador, 10),
                    recortar(c.arg1, 10),
                    recortar(c.arg2, 10),
                    recortar(c.resultado, 10),
                    c.comentario);
        }
        System.out.println(borde);
        System.out.println(ANSI_BLUE + "  Total de instrucciones: " + codigo.size() + ANSI_RESET);
    }

    /** true si hay algun error que no sea advertencia. */
    private static boolean hayErrores() {
        for (int i = 0; i < EasyCompiler.listaErrores.size(); i++) {
            if (!"Advertencia".equals(EasyCompiler.listaErrores.get(i).tipo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * --codigo-optimizado: los cuadruplos que dejo el optimizador, donde vive
     * cada temporal que sobrevivio, y las metricas del codigo antes y despues.
     * Las metricas dinamicas salen de ejecutar los dos codigos en el simulador
     * con la entrada fija de las pruebas.
     */
    private static void imprimirCodigoOptimizado(AlgoritmosOptimizacion.Resultado r, String motivo) {
        System.out.println(ANSI_BLUE + "\n============================================================");
        System.out.println(" CODIGO OPTIMIZADO - CUADRUPLOS");
        System.out.println("============================================================" + ANSI_RESET);
        if (r == null) {
            System.out.println(ANSI_YELLOW + "  (no se optimizo) " + motivo + "." + ANSI_RESET);
            return;
        }
        if (r.codigo.isEmpty()) {
            System.out.println(ANSI_YELLOW + "  (vacio) No queda ninguna instruccion." + ANSI_RESET);
        } else {
            imprimirCuadruplos(r.codigo);
        }

        java.util.List<String[]> temporales = r.temporales();
        if (!temporales.isEmpty()) {
            System.out.println(ANSI_BLUE + "\n TEMPORALES - DIRECCIONES" + ANSI_RESET);
            String borde = "+----------+-------+-------+-------+";
            System.out.println(borde);
            System.out.printf("| %-8s | %-5s | %-5s | %-5s |%n", "TEMPORAL", "TIPO", "ANCHO", "DIR");
            System.out.println(borde);
            for (String[] t : temporales) {
                System.out.printf("| %-8s | %-5s | %-5s | %-5s |%n", t[0], t[1], t[2], t[3]);
            }
            System.out.println(borde);
        }

        CostosEjecucion costos = new CostosEjecucion();
        MetricasCodigo antes = r.metricasOriginal(costos, true);
        MetricasCodigo despues = r.metricasOptimizado(costos, true);
        System.out.println(ANSI_BLUE + "\n RESUMEN DE LA OPTIMIZACION" + ANSI_RESET);
        String borde = "+------------------------+------------+------------+---------+";
        System.out.println(borde);
        System.out.printf("| %-22s | %10s | %10s | %7s |%n", "METRICA", "ORIGINAL", "OPTIMIZADO", "CAMBIO");
        System.out.println(borde);
        for (String[] f : MetricasCodigo.comparar(antes, despues)) {
            System.out.printf("| %-22s | %10s | %10s | %7s |%n", f[0], f[1], f[2], f[3]);
        }
        System.out.println(borde);
        System.out.println(ANSI_BLUE + "  Las metricas dinamicas se miden ejecutando el codigo en el simulador,"
                + "\n  con la entrada fija 3, 5, 1, 0, 2, 4 para cada LEER." + ANSI_RESET);
    }

    /** --bitacora-optimizacion: cada transformacion, por fase, y el grafo de flujo final. */
    private static void imprimirBitacoraOptimizacion(AlgoritmosOptimizacion.Resultado r, String motivo) {
        System.out.println(ANSI_BLUE + "\n============================================================");
        System.out.println(" BITACORA DE LA OPTIMIZACION");
        System.out.println("============================================================" + ANSI_RESET);
        if (r == null) {
            System.out.println(ANSI_YELLOW + "  (no se optimizo) " + motivo + "." + ANSI_RESET);
            return;
        }
        for (String linea : r.bitacora()) {
            System.out.println(linea.isEmpty() ? "" : "  " + linea);
        }
    }

    /**
     * Imprime donde vive cada temporal: la otra mitad de lo que necesita la
     * fase siguiente. Las variables tienen su direccion en la tabla de
     * simbolos; los temporales, que no estan ahi, la tienen aqui.
     */
    private static void imprimirTemporales() {
        int n = GeneradorCodigo.cantidadDeTemporales();
        if (n == 0) {
            return;
        }
        int datos = TablaSimbolos.tamanoDelMarco();
        int bytes = 0;
        for (int i = 0; i < n; i++) {
            bytes += GeneradorCodigo.anchoDeTemporal(i);
        }

        System.out.println(ANSI_BLUE + "\n TEMPORALES - DIRECCIONES" + ANSI_RESET);
        String borde = "+----------+-------+-------+-------+";
        System.out.println(borde);
        System.out.printf("| %-8s | %-5s | %-5s | %-5s |%n", "TEMPORAL", "TIPO", "ANCHO", "DIR");
        System.out.println(borde);
        for (int i = 0; i < n; i++) {
            System.out.printf("| %-8s | %-5s | %-5d | %-5d |%n",
                    GeneradorCodigo.nombreDeTemporal(i),
                    GeneradorCodigo.tipoDeTemporal(i),
                    GeneradorCodigo.anchoDeTemporal(i),
                    GeneradorCodigo.direccionDeTemporal(i));
        }
        System.out.println(borde);
        System.out.println(ANSI_BLUE + "  Datos: " + datos + " bytes" + rango(0, datos)
                + "   |   Temporales: " + n + ", " + bytes + " bytes" + rango(datos, bytes)
                + "   |   Total: " + (datos + bytes) + " bytes");
    }

    /**
     * Ordena los errores por posicion en el codigo fuente.
     *
     * Hace falta porque las tres fases llenan la lista en momentos distintos:
     * los lexicos y sintacticos durante el parseo, y los semanticos despues, en
     * la pasada sobre el arbol. Sin ordenar, un error de la linea 3 aparece
     * detras de uno de la linea 100 y el reporte deja de leerse de arriba abajo.
     *
     * La ordenacion es ESTABLE, asi que dos errores en la misma posicion
     * conservan el orden en que se detectaron: primero el lexico, luego el
     * sintactico, luego el semantico, que es el orden en que se explican.
     */
    private static void ordenarErrores() {
        java.util.Collections.sort(EasyCompiler.listaErrores,
                new java.util.Comparator<ErrorCompilador>() {
                    public int compare(ErrorCompilador a, ErrorCompilador b) {
                        if (a.linea != b.linea)
                            return a.linea - b.linea;
                        return a.columna - b.columna;
                    }
                });
    }

    /**
     * Imprime la tabla de símbolos que las acciones semánticas fueron llenando
     * durante el análisis sintáctico.
     *
     * Cada renglón es una entrada instalada por agregarTipo(), la función que
     * Aho deja como supuesta en el Ejemplo 5.10 (Compiladores, 2a ed., pág. 316).
     */
    private static void imprimirTablaSimbolos() {
        System.out.println(ANSI_BLUE + "\n============================================================");
        System.out.println(" \uD83D\uDCD3 TABLA DE SIMBOLOS");
        System.out.println("============================================================" + ANSI_RESET);

        if (TablaSimbolos.estaVacia()) {
            System.out.println(ANSI_YELLOW
                    + "  (vacia) No se declaro ningun identificador en este programa."
                    + ANSI_RESET);
            return;
        }

        // La tabla de direcciones (tema 1.6) no es una tabla aparte: son tres
        // columnas mas de esta, porque describen los MISMOS simbolos. ANCHO son
        // los bytes que ocupa, DESPL su desplazamiento dentro de su bloque y DIR
        // la direccion absoluta (Aho, Compiladores 2a ed., seccion 6.3.4).
        String linea = "+------+----------------------+-------+-----------+------------+-------+--------+------+--------------------+-------+---------+-------+-------+-------+";

        System.out.println(linea);
        System.out.printf(
                "| %-4s | %-20s | %-5s | %-9s | %-10s | %-5s | %-6s | %-4s | %-18s | %-5s | %-7s | %-5s | %-5s | %-5s |%n",
                "#", "NOMBRE", "TIPO", "CATEGORIA", "DIMENSION", "NIVEL", "BLOQUE", "INIC", "VALOR", "LINEA", "COLUMNA",
                "ANCHO", "DESPL", "DIR");
        System.out.println(linea);

        java.util.List<Simbolo> simbolos = TablaSimbolos.obtenerTodos();
        for (int i = 0; i < simbolos.size(); i++) {
            Simbolo s = simbolos.get(i);
            System.out.printf(
                    "| %-4d | %-20s | %-5s | %-9s | %-10s | %-5d | %-6d | %-4s | %-18s | %-5d | %-7d | %-5d | %-5d | %-5d |%n",
                    (i + 1),
                    recortar(s.nombre, 20),
                    s.tipo,
                    s.categoria,
                    recortar(s.dimensiones, 10),
                    s.nivel,
                    s.bloque,
                    (s.inicializada ? "SI" : "NO"),
                    recortar(s.valor, 18),
                    s.linea,
                    s.columna,
                    s.ancho,
                    s.desplazamiento,
                    s.direccion);
        }
        System.out.println(linea);
        System.out.println(ANSI_BLUE + "  Total de simbolos declarados: " + simbolos.size()
                + "   |   Memoria total para datos: " + TablaSimbolos.tamanoDelMarco() + " bytes" + ANSI_RESET);
        System.out.println(ANSI_BLUE
                + "\n  ANCHO en bytes: ENT 4 | DEC 8 | BOOL 1 | LETRA 2 | TXT 4 (referencia)." + ANSI_RESET);
    }

    /**
     * Corta un texto que no cabe en su columna y lo cierra con puntos suspensivos.
     */
    /** " (desde-hasta)" de un bloque de memoria; nada si esta vacio. */
    private static String rango(int inicio, int bytes) {
        return (bytes <= 0) ? "" : " (" + inicio + "-" + (inicio + bytes - 1) + ")";
    }

    private static String recortar(String texto, int ancho) {
        if (texto == null)
            return "-";
        if (texto.length() <= ancho)
            return texto;
        return texto.substring(0, ancho - 3) + "...";
    }

    /**
     * Imprime el árbol sintáctico construido por JJTree durante el análisis.
     *
     * @param raiz     nodo Programa devuelto por el parser, o null si ni siquiera
     *                 se pudo llegar a construirlo.
     * @param completo true muestra la derivación literal de la gramática; false
     *                 colapsa los niveles intermedios de las expresiones.
     */
    private static void imprimirArbol(SimpleNode raiz, boolean completo) {
        System.out.println(ANSI_BLUE + "\n============================================================");
        System.out.println(" 🌳 ARBOL SINTACTICO" + (completo ? " (derivacion completa)" : " (colapsado)"));
        System.out.println("============================================================" + ANSI_RESET);

        if (EasyCompiler.arbolParcial) {
            System.out.println(ANSI_YELLOW
                    + "  ⚠️  El analisis se interrumpio por un error irrecuperable:"
                    + "\n     el arbol de abajo esta INCOMPLETO y solo llega hasta ese punto."
                    + ANSI_RESET);
        }

        System.out.print(ImpresorArbol.dibujar(raiz, completo));

        if (!completo) {
            System.out.println(ANSI_BLUE
                    + "  (usa --arbol-sintactico-completo para ver la derivacion sin colapsar)"
                    + ANSI_RESET);
        }
    }
}
