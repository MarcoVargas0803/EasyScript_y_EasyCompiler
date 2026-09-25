// Archivo: Tipo.java
//
// Los tipos de EasyScript, mas el centinela ERROR.
//
// ERROR es el "type_error" de Aho (Compiladores, 2a ed., seccion 6.5.2): cuando
// una comprobacion falla, el resultado es ERROR y ese valor se propaga hacia
// arriba SIN volver a quejarse. Es lo que evita que un solo fallo real produzca
// una avalancha de mensajes derivados.
public enum Tipo {

    ENT  ("ENT",   4),   // entero de 32 bits
    DEC  ("DEC",   8),   // punto flotante de doble precision
    BOOL ("BOOL",  1),   // un byte
    LETRA("LETRA", 2),   // caracter UTF-16
    TXT  ("TXT",   4),   // referencia a la cadena (los caracteres viven aparte)
    ERROR("ERROR", 0);   // centinela: no ocupa memoria, no se reporta

    /** Lexema con el que se escribe el tipo en el codigo fuente. */
    public final String lexema;

    /** Bytes que ocupa un valor de este tipo (tema 1.6, Aho seccion 6.3.4). */
    public final int ancho;

    private Tipo(String lexema, int ancho) {
        this.lexema = lexema;
        this.ancho = ancho;
    }

    /** Convierte "ENT", "DEC", ... en su Tipo. Devuelve ERROR si no lo reconoce. */
    public static Tipo desdeLexema(String s) {
        if (s == null) return ERROR;
        for (Tipo t : values()) {
            if (t.lexema.equals(s)) return t;
        }
        return ERROR;
    }

    public boolean esNumerico() {
        return this == ENT || this == DEC;
    }

    public boolean esError() {
        return this == ERROR;
    }

    @Override
    public String toString() {
        return lexema;
    }
}
