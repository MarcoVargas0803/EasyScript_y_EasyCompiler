// Archivo: ErrorCompilador.java
public class ErrorCompilador {
    public String tipo;
    public int linea;
    public int columna;
    public String detalle;
    public String consejo;

    public ErrorCompilador(String tipo, int linea, int columna, String detalle, String consejo) {
        this.tipo = tipo;
        this.linea = linea;
        this.columna = columna;
        this.detalle = detalle;
        this.consejo = consejo;
    }
}