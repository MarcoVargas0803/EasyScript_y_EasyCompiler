// Archivo: Atributo.java
//
// Un operando de la pila semantica.
//
// Es lo que Aho (Compiladores, 2a ed., seccion 5.1.1) llama el conjunto de
// atributos de un simbolo gramatical. Cuando el analizador semantico evalua una
// expresion, cada hoja apila un Atributo y cada operador saca dos y apila el
// resultado; al final queda uno solo, que describe la expresion entera.
//
// Guarda mas cosas que el tipo porque las reglas semanticas las necesitan:
//   - 'asignable' decide si algo puede estar a la izquierda de un '=' o dentro
//     de un LEER. Un literal no lo es; una constante tampoco.
//   - 'valorConstante' permite detectar en compilacion divisiones entre cero e
//     indices fuera de rango, sin ejecutar nada.
//   - 'token' es lo que hace que el error salga en la linea y columna exactas.
import java.util.List;

public class Atributo {

    public Tipo    tipo = Tipo.ERROR;
    public String  lexema = "?";
    public boolean esLiteral;
    public boolean asignable;
    public String  valorConstante;   // null si no se conoce en compilacion
    public Simbolo simbolo;          // entrada de la tabla, o null
    public Token   token;            // de donde salio, para ubicar el error

    /**
     * Donde vive el valor en el codigo de tres direcciones: el nombre de la
     * variable, el literal tal cual, o el temporal que lo contiene ("t3").
     *
     * Es lo que separa el LEXEMA (lo que el programador escribio, util para los
     * mensajes de error) de la DIRECCION (lo que el codigo generado manipula).
     * Para "A + B" el lexema del resultado es "A + B" y su lugar es "t1".
     */
    public String  lugar = "?";

    // ------------------------------------------------------------------
    // Representacion por SALTOS (Aho, Compiladores 2a ed., secciones 6.6 y 6.7)
    //
    // Una condicion tiene dos formas posibles en el codigo generado:
    //
    //   - como VALOR: se calcula un booleano y se guarda en 'lugar'.
    //   - como SALTOS: no se calcula nada; el codigo simplemente salta a un
    //     sitio si la condicion es cierta y a otro si es falsa.
    //
    // La segunda es la que permite el cortocircuito: si en "A && B" ya se sabe
    // que A es falsa, se salta y B ni se evalua.
    //
    // El problema es que al generar el salto todavia no se sabe a donde va: el
    // destino esta mas adelante en el codigo, sin emitir. Por eso el salto se
    // emite con el destino en blanco y su numero de instruccion se apunta en una
    // de estas listas; cuando el destino se conoce, se rellenan todas de golpe.
    // Eso es el backpatching.
    // ------------------------------------------------------------------

    /** Instrucciones cuyo destino hay que rellenar con el "sitio si es cierta". */
    public List<Integer> listaVerdadero;

    /** Instrucciones cuyo destino hay que rellenar con el "sitio si es falsa". */
    public List<Integer> listaFalso;

    // ------------------------------------------------------------------
    // Fabricas. Son metodos y no constructores porque los casos tienen poco
    // en comun entre si y ocho constructores del mismo aridad se confunden.
    // ------------------------------------------------------------------

    /** Literal escrito en el codigo: 5, 3.14, "hola", 'a', VERDADERO. */
    public static Atributo deLiteral(Token t) {
        Atributo a = new Atributo();
        a.token = t;
        a.esLiteral = true;
        a.asignable = false;
        if (t == null) return a;
        a.lexema = t.image;
        a.lugar = t.image;          // un literal es su propio valor
        a.valorConstante = t.image;
        switch (t.kind) {
            case EasyCompilerConstants.NUM_ENTERO:         a.tipo = Tipo.ENT;   break;
            case EasyCompilerConstants.NUM_DECIMAL:        a.tipo = Tipo.DEC;   break;
            case EasyCompilerConstants.CADENA:             a.tipo = Tipo.TXT;   break;
            case EasyCompilerConstants.VALOR_LETRA:        a.tipo = Tipo.LETRA; break;
            case EasyCompilerConstants.VERDADERO_BOOLEANO: a.tipo = Tipo.BOOL;  break;
            case EasyCompilerConstants.FALSO_BOOLEANO:     a.tipo = Tipo.BOOL;  break;
            default:                                       a.tipo = Tipo.ERROR; break;
        }
        return a;
    }

    /** Referencia a un identificador ya declarado. */
    public static Atributo deSimbolo(Token t, Simbolo s) {
        Atributo a = new Atributo();
        a.token = t;
        a.simbolo = s;
        a.lexema = (t == null) ? "?" : t.image;
        a.lugar = a.lexema;         // una variable se referencia por su nombre
        a.tipo = Tipo.desdeLexema(s.tipo);
        a.esLiteral = false;
        // Una constante se puede leer pero no reescribir: ese es justamente el
        // sentido de declararla como constante.
        a.asignable = !"CONSTANTE".equals(s.categoria);
        return a;
    }

    /** Resultado intermedio de un operador: "t = A + B". */
    public static Atributo temporal(Tipo tipo, String lexema, Token t) {
        return temporal(tipo, lexema, "?", t);
    }

    /** Resultado intermedio que ya vive en un temporal del codigo generado. */
    public static Atributo temporal(Tipo tipo, String lexema, String lugar, Token t) {
        Atributo a = new Atributo();
        a.tipo = tipo;
        a.lexema = lexema;
        a.lugar = (lugar == null) ? "?" : lugar;
        a.token = t;
        a.asignable = false;
        return a;
    }

    /**
     * Operando invalido. Su tipo ERROR se propaga hacia arriba SIN volver a
     * generar mensajes (Aho, seccion 6.5.2): es lo que evita que un solo fallo
     * real se convierta en veinte quejas derivadas.
     */
    public static Atributo error(Token t) {
        Atributo a = new Atributo();
        a.tipo = Tipo.ERROR;
        a.token = t;
        a.lexema = (t == null) ? "?" : t.image;
        return a;
    }

    /**
     * Condicion en forma de saltos, todavia sin destino.
     *
     * Quien la construya debe llenar listaVerdadero y listaFalso; quien la
     * consuma debe rellenarlas, o el codigo quedara con saltos a ninguna parte.
     */
    public static Atributo saltos(Tipo tipo, String lexema, Token t) {
        Atributo a = new Atributo();
        a.tipo = tipo;
        a.lexema = lexema;
        a.token = t;
        a.asignable = false;
        return a;
    }

    /** true si el valor vive como saltos pendientes y no como dato. */
    public boolean esSaltos() {
        return listaVerdadero != null || listaFalso != null;
    }

    public boolean esError() {
        return tipo == null || tipo == Tipo.ERROR;
    }

    /** true si el valor se conoce en tiempo de compilacion. */
    public boolean esConstanteConocida() {
        return valorConstante != null;
    }

    public int linea() {
        return (token == null) ? -1 : token.beginLine;
    }

    public int columna() {
        return (token == null) ? -1 : token.beginColumn;
    }

    public String toString() {
        return lexema + ":" + tipo;
    }
}
