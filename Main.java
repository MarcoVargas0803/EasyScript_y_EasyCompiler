import java.io.FileInputStream;
import java.io.FileNotFoundException;

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
                System.out.println("Error: Debes especificar un archivo. Ejemplo: java Main codigo1.txt");
                System.out.println("Opciones: --arbol-completo   Muestra la derivación literal, sin colapsar");
                System.out.println("                             los niveles intermedios de las expresiones.");
                System.out.println("          --codigo-intermedio Muestra el codigo de tres direcciones que genera");
                System.out.println("                             el esquema de traduccion.");
                return;
            }

            // La bandera puede venir en cualquier posición, así que args[0] sigue
            // siendo el nombre del archivo.
            boolean arbolCompleto = false;
            // El codigo intermedio se genera SIEMPRE, pero no se imprime salvo
            // que se pida. La consola se queda con sus tres bloques de siempre:
            // arbol, tabla de simbolos y errores.
            boolean mostrarCodigo = false;
            for (String arg : args) {
                if (arg.equals("--arbol-completo")) {
                    arbolCompleto = true;
                }
                if (arg.equals("--codigo-intermedio")) {
                    mostrarCodigo = true;
                }
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
            // tabla de simbolos que se imprime justo despues, y sus errores
            // tienen que entrar en listaErrores antes del reporte final.
            // ---------------------------------------------------------------
            new AnalizadorSemantico().analizar(raiz);

            imprimirArbol(raiz, arbolCompleto);

            imprimirTablaSimbolos();

            if (mostrarCodigo) {
                imprimirCodigoIntermedio();
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
                System.out.println(ANSI_GREEN + ">> ¡Excelente! No se encontraron errores en el código fuente." + ANSI_RESET);
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
                    System.out.printf( ANSI_BLUE +"\n[ Error %d ] --- Tipo: %s | Línea: %d | Columna: %d \n" + ANSI_RESET,
                            (i + 1), etiquetaTipo, error.linea, error.columna);

                    // Fila 2 y 3: Detalle y Consejo
                    System.out.println(ANSI_RED + "  ❌ Detalle : " + error.detalle + ANSI_RESET);
                    System.out.println("  💡 Consejo : " + error.consejo + ANSI_RESET);
                }

                System.out.println(color + "\n============================================================" + ANSI_RESET);





                /*
                // Dibujamos la cabecera de la tabla
                System.out.println("Total de errores detectados: " + EasyCompiler.listaErrores.size());
                System.out.println(
                        "==================================================== TABLA DE ERRORES ==================================================================================");
                System.out.printf("| %-15s | %-7s | %-9s | %-45s | %-60s |\n", "TIPO", "LINEA", "COLUMNA",
                        "DETALLE DEL ERROR", "CONSEJO");
                System.out.println(
                        "--------------------------------------------------------------------------------------------------------------------------------------------------------");

                // Recorremos la lista e imprimimos cada error con formato
                for (ErrorCompilador error : EasyCompiler.listaErrores) {
                    System.out.printf("| %-15s | %-7d | %-9d | %-45s | %-60s |\n", error.tipo, error.linea,
                            error.columna, error.detalle, error.consejo);
                }

                System.out.println(
                        "========================================================================================================================================================");
            */
            }
        } catch (FileNotFoundException e) {
            System.out.println("El archivo " + args[0] + " no existe en la carpeta.");
        }
    }

    /**
     * Imprime el codigo de tres direcciones que genero el esquema de traduccion.
     *
     * Solo se llama con la bandera --codigo-intermedio. La salida normal del
     * compilador no lo incluye, porque esta fase todavia es preliminar y la
     * consola debe seguir mostrando lo mismo de siempre.
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

        java.util.List<String> rotas = GeneradorCodigo.etiquetasRotas();
        if (!rotas.isEmpty()) {
            System.out.println(ANSI_RED + "  ERROR INTERNO: hay saltos a etiquetas que no existen: "
                + rotas + ANSI_RESET);
        }
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
                    if (a.linea != b.linea) return a.linea - b.linea;
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
        System.out.printf("| %-4s | %-20s | %-5s | %-9s | %-10s | %-5s | %-6s | %-4s | %-18s | %-5s | %-7s | %-5s | %-5s | %-5s |%n",
                "#", "NOMBRE", "TIPO", "CATEGORIA", "DIMENSION", "NIVEL", "BLOQUE", "INIC", "VALOR", "LINEA", "COLUMNA",
                "ANCHO", "DESPL", "DIR");
        System.out.println(linea);

        java.util.List<Simbolo> simbolos = TablaSimbolos.obtenerTodos();
        for (int i = 0; i < simbolos.size(); i++) {
            Simbolo s = simbolos.get(i);
            System.out.printf("| %-4d | %-20s | %-5s | %-9s | %-10s | %-5d | %-6d | %-4s | %-18s | %-5d | %-7d | %-5d | %-5d | %-5d |%n",
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
            + "  NIVEL = profundidad de anidamiento; BLOQUE = ambito concreto (el SI y el SINO"
            + "\n  comparten nivel pero son bloques distintos)."
            + "\n  ANCHO en bytes: ENT 4 | DEC 8 | BOOL 1 | LETRA 2 | TXT 4 (referencia)."
            + "\n  DESPL se reinicia en cada bloque: dos bloques hermanos comparten direcciones porque"
            + "\n  no estan vivos a la vez." + ANSI_RESET);
    }

    /** Corta un texto que no cabe en su columna y lo cierra con puntos suspensivos. */
    private static String recortar(String texto, int ancho) {
        if (texto == null) return "-";
        if (texto.length() <= ancho) return texto;
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
                + "  (usa --arbol-completo para ver la derivacion sin colapsar)"
                + ANSI_RESET);
        }
    }
}
