// Archivo: Simbolo.java
//
// Una entrada de la tabla de simbolos.
//
// Corresponde a lo que Aho (Compiladores, 2a ed., seccion 5.1.1, pag. 304)
// llama "id.entrada": el objeto de la tabla de simbolos al que apunta el valor
// lexico del identificador. Cada campo de esta clase es un atributo del simbolo;
// unos llegan sintetizados desde abajo (el tipo, que sube de TipoDato) y otros
// se registran como efecto adicional de la accion semantica (linea, columna).
public class Simbolo {

    public String nombre;       // Lexema del identificador (id.image)
    public String tipo;         // ENT, DEC, BOOL, LETRA, TXT  <- atributo heredado de TipoDato()
    public String categoria;    // VARIABLE, CONSTANTE, ARREGLO o MATRIZ
    public String dimensiones;  // "[9]" o "[3][4]" para estructuras; "-" para escalares

    // Identidad del bloque donde se declaro. Son dos datos distintos:
    //   nivel  -> profundidad de anidamiento (el cuerpo de INICIO{} es 1)
    //   bloque -> identificador unico de ESE bloque en concreto
    // Se necesitan los dos porque dos bloques hermanos (el SI y el SINO de un
    // mismo condicional) estan al mismo nivel pero son ambitos independientes:
    // el mismo nombre puede declararse en ambos sin que haya doble declaracion.
    public int    nivel;
    public int    bloque;

    public boolean inicializada;// true si la declaracion traia " = <expresion>"
    public String valor;        // Texto de la expresion inicializadora, o "-"
    public int    linea;
    public int    columna;

    public Simbolo(String nombre, String tipo, String categoria, String dimensiones,
                   int nivel, int bloque, boolean inicializada, String valor,
                   int linea, int columna) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.categoria = categoria;
        this.dimensiones = dimensiones;
        this.nivel = nivel;
        this.bloque = bloque;
        this.inicializada = inicializada;
        this.valor = valor;
        this.linea = linea;
        this.columna = columna;
    }

    // ------------------------------------------------------------------
    // Campos que llena el analizador semantico (no el constructor)
    // ------------------------------------------------------------------

    /**
     * Bloque que ENCIERRA al bloque donde se declaro este simbolo; 0 si es el
     * bloque mas externo.
     *
     * Se guarda aunque durante el recorrido la pila de ambitos ya diga lo mismo,
     * porque la pila se vacia al terminar y esta informacion tiene que
     * sobrevivir: sin ella, al imprimir la tabla no hay forma de saber que
     * bloque estaba dentro de cual.
     */
    public int bloquePadre;

    /** true en cuanto el nombre se lee o se escribe en alguna parte. */
    public boolean usado;

    /**
     * true cuando el simbolo ya tiene un valor: porque se declaro con uno,
     * porque se le asigno despues, o porque un LEER lo lleno.
     *
     * Es distinto de 'inicializada', que responde a "la DECLARACION traia un
     * = valor" y es lo que se imprime en la tabla. Este campo sigue la pista del
     * valor a lo largo del programa, y es el que permite avisar de una variable
     * que se usa antes de tener nada dentro.
     *
     * El seguimiento es textual, no de flujo: no sabe si un SI se ejecuta o no.
     * Basta para el aviso, que por eso es advertencia y no error.
     */
    public boolean tieneValor;

    /**
     * Tamanos declarados. Un escalar deja los dos en 0; un ARREGLO usa filas
     * como su unica dimension; una MATRIZ usa las dos. Se guardan como numero
     * ademas de como texto en 'dimensiones' porque comprobar que un indice cae
     * dentro del rango exige aritmetica, no una cadena.
     */
    public int filas;
    public int columnas;

    // ------------------------------------------------------------------
    // Tabla de direcciones (Aho, Compiladores 2a ed., seccion 6.3.4, Fig. 6.17)
    //
    // Cada declaracion reserva 'ancho' bytes a partir de 'desplazamiento',
    // contado desde el principio de su bloque. La direccion absoluta es la base
    // del bloque mas ese desplazamiento.
    //
    // El desplazamiento se reinicia en cada bloque, y dos bloques HERMANOS
    // arrancan en la misma base: no estan vivos a la vez, asi que pueden
    // compartir el mismo espacio.
    // ------------------------------------------------------------------

    /** Bytes que ocupa un solo elemento (el tipo base). */
    public int anchoElemento;

    /** Bytes que ocupa el simbolo entero: el elemento por cuantos haya. */
    public int ancho;

    /** Desplazamiento desde el inicio de su bloque. */
    public int desplazamiento;

    /** Direccion absoluta: base del bloque + desplazamiento. */
    public int direccion;
}
