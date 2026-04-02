import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String args[]) {
        try {
            // Verificamos que al ejecutar envíen el nombre del archivo
            if (args.length == 0) {
                System.out.println("Error: Debes especificar un archivo. Ejemplo: java EasyCompiler codigo1.txt");
                return;
            }

            // Abrimos el archivo de texto
            FileInputStream archivo = new FileInputStream(args[0]);
            EasyCompiler compilador = new EasyCompiler(archivo);

            System.out.println("=== Iniciando analisis lexico de: " + args[0] + " ===");

            // SOLUCIÓN A LA EXCEPCIÓN: Try-catch para el ParseException
            try {
                compilador.Programa();
                System.out.println(">> Analisis completado.");
            } catch (ParseException e) {
                System.out.println("Error critico de sintaxis no recuperable: " + e.getMessage());
            }

            System.out.println("=== Analisis lexico finalizado ===");

            if (EasyCompiler.listaErrores.isEmpty()) {
                System.out.println(">> ¡Excelente! No se encontraron errores en el codigo fuente.");
            } else {
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
            }

        } catch (FileNotFoundException e) {
            System.out.println("El archivo " + args[0] + " no existe en la carpeta.");
        } catch (TokenMgrError e) {
            // Si el usuario escribe un símbolo que no definimos
            System.out.println("Error critico del analizador: " + e.getMessage());
        }
    }
}
