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

            // Creamos ArrayList para los errores.
            List<ErrorCompilador> listaErrores = new ArrayList<>();

            // Le pedimos el primer token
            Token token = compilador.getNextToken();

            // Ciclo que se repite hasta llegar al final del archivo (EOF)
            while (token.kind != EasyCompilerConstants.EOF) {

                // Atrapamos TODOS los tokens trampa definidos en el ArrayList

                /*
                 * --ERRORES LEXICOS --
                 * 
                 */
                // Creamos un string para pasarlo a todos los que son Errores Léxicos.
                String ErrorLexicoTipo = "Error Léxico";

                // 1. ERROR_IDENTIFICADOR_INVALIDO
                if (token.kind == EasyCompilerConstants.ERROR_IDENTIFICADOR_INVALIDO) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Identificador no valido '" + token.image + "'",
                            "Toda variable/constante debe iniciar con mayuscula."));
                }
                // 2. ERROR_TEXTO
                else if (token.kind == EasyCompilerConstants.ERROR_TEXTO) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Cadena de texto sin cerrar.",
                            "Cierra la cadena de texto con comillas dobles."));
                }
                // 3. ERROR_LONGITUD_CARACTER_SIMBOLO_NO_VALIDO
                else if (token.kind == EasyCompilerConstants.ERROR_LONGITUD_CARACTER) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Formato de caracter invalido: " + token.image,
                            "Longitud mayor a 1 o caracter no reconocido para LETRA."));
                }
                // 4. ERROR_CARACTER
                else if (token.kind == EasyCompilerConstants.ERROR_CARACTER) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Caracter sin cerrar.",
                            "Cierra el caracter con comillas simples."));
                }
                // 5. ERROR_NUMERO_INVALIDO
                else if (token.kind == EasyCompilerConstants.ERROR_NUMERO_INVALIDO) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Formato decimal invalido '" + token.image + "'",
                            "Sigue la regla para decimales. Ejemplo: 3.1416"));
                }
                // 6. ERROR_OPERADOR_INCOMPLETO
                else if (token.kind == EasyCompilerConstants.ERROR_OPERADOR_INCOMPLETO) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Operador incompleto o no valido: '" + token.image + "'",
                            "Los operadores en EasyScript son dobles (&&, ||, !!)."));
                }
                // 7. ERROR_SIMBOLO
                else if (token.kind == EasyCompilerConstants.ERROR_SIMBOLO) {
                    listaErrores.add(new ErrorCompilador(ErrorLexicoTipo, token.beginLine, token.beginColumn,
                            "Simbolo no valido '" + token.image + "'",
                            "Usa la simbologia definida en el manual de usuario."));
                }
                // En caso de pedir los tokens correctos (Analisis Léxico)
                else {

                    /*
                     * -- Esta parte del análisis léxico ya no es necesario
                     */

                    // String tipoToken = EasyCompilerConstants.tokenImage[token.kind];
                    // System.out.println( token.image + " | " + tipoToken );

                }

                // Avanzamos al siguiente token
                token = compilador.getNextToken();
            }

            System.out.println("=== Analisis lexico finalizado ===");

            if (listaErrores.isEmpty()) {
                System.out.println(">> ¡Excelente! No se encontraron errores en el codigo fuente.");
            } else {
                // Dibujamos la cabecera de la tabla
                System.out.println("Total de errores detectados: " + listaErrores.size());
                System.out.println(
                        "==================================================== TABLA DE ERRORES ==================================================================================");
                System.out.printf("| %-15s | %-7s | %-9s | %-45s | %-60s |\n", "TIPO", "LINEA", "COLUMNA",
                        "DETALLE DEL ERROR", "CONSEJO");
                System.out.println(
                        "--------------------------------------------------------------------------------------------------------------------------------------------------------");

                // Recorremos la lista e imprimimos cada error con formato
                for (ErrorCompilador error : listaErrores) {
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
