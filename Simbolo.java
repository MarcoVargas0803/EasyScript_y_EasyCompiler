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
}
