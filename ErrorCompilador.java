// Archivo: ErrorCompilador.java
public class ErrorCompilador {
    public String tipo;      // "Léxico" | "Sintáctico" | "Semántico" | "Advertencia"
    public int linea;
    public int columna;
    public String detalle;
    public String consejo;

    // Codigo corto y estable del error: SEM-01, ADV-03, ...
    //
    // Sirve para poder decir "el archivo de pruebas debe disparar SEM-12" sin
    // depender del texto del mensaje, que se puede reescribir. Los errores
    // lexicos y sintacticos, que ya existian antes de la fase semantica, se
    // quedan con "-".
    public String codigo;

    public ErrorCompilador(String tipo, int linea, int columna, String detalle, String consejo) {
        this(tipo, "-", linea, columna, detalle, consejo);
    }

    public ErrorCompilador(String tipo, String codigo, int linea, int columna,
                           String detalle, String consejo) {
        this.tipo = tipo;
        this.codigo = codigo;
        this.linea = linea;
        this.columna = columna;
        this.detalle = detalle;
        this.consejo = consejo;
    }
}
