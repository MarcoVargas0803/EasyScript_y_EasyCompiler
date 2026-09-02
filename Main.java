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
                return;
            }

            // La bandera puede venir en cualquier posición, así que args[0] sigue
            // siendo el nombre del archivo.
            boolean arbolCompleto = false;
            for (String arg : args) {
                if (arg.equals("--arbol-completo")) {
                    arbolCompleto = true;
                }
            }

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

            imprimirArbol(raiz, arbolCompleto);

            if (EasyCompiler.listaErrores.isEmpty()) {
                System.out.println(ANSI_GREEN + ">> ¡Excelente! No se encontraron errores en el código fuente." + ANSI_RESET);
            } else {

                // Cabecera principal
                System.out.println(ANSI_RED + "\n============================================================");
                System.out.println(" ⚠\uFE0F SE ENCONTRARON " + EasyCompiler.listaErrores.size() + " ERRORES DURANTE LA COMPILACIÓN ⚠\uFE0F");
                System.out.println("============================================================" + ANSI_RESET);

                // Recorremos la lista e imprimimos cada error en formato de "Tarjeta"
                for (int i = 0; i < EasyCompiler.listaErrores.size(); i++) {
                    ErrorCompilador error = EasyCompiler.listaErrores.get(i);

                    // Fila 1: Encabezado del error con su ubicación
                    System.out.printf( ANSI_BLUE +"\n[ Error %d ] --- Tipo: %s | Línea: %d | Columna: %d \n" + ANSI_RESET,
                            (i + 1), error.tipo, error.linea, error.columna);

                    // Fila 2 y 3: Detalle y Consejo
                    System.out.println(ANSI_RED + "  ❌ Detalle : " + error.detalle + ANSI_RESET);
                    System.out.println("  💡 Consejo : " + error.consejo + ANSI_RESET);
                }

                System.out.println(ANSI_RED + "\n============================================================" + ANSI_RESET);





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
