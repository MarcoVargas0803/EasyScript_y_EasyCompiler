// Archivo: Cuadruplo.java
//
// Una instruccion de codigo de tres direcciones, en forma de cuadruplo
// (Aho, Compiladores 2a ed., seccion 6.2.2).
//
// Un cuadruplo son cuatro casillas: el operador y hasta tres direcciones. La
// idea de fondo es que cada instruccion haga UNA sola operacion sobre nombres
// ya calculados, de modo que "A + B * C" deje de ser un arbol y pase a ser una
// secuencia plana:
//
//      t1 = B * C
//      t2 = A + t1
//
// Esa forma plana es la que se puede recorrer linealmente para optimizar o para
// traducir a ensamblador, cosa que el arbol no permite.
//
// Se guarda ademas un comentario legible ("t1 = B * C") porque las cuatro
// casillas sueltas son dificiles de leer de un vistazo, y el objetivo de esta
// fase es tanto generar el codigo como poder explicarlo.
public class Cuadruplo {

    /** Numero de instruccion. Es la direccion a la que apuntan los saltos. */
    public int indice;

    /**
     * Operador. Ademas de los del lenguaje se usan estos internos:
     *
     *   "="          copia:             res = arg1
     *   "ampliar"    conversion ENT->DEC
     *   "etiqueta"   destino de salto (no ejecuta nada)
     *   "ir_a"       salto incondicional a res
     *   "si_falso"   salta a res si arg1 es falso
     *   "si_igual"   salta a res si arg1 == arg2
     *   "=[]"        lectura indexada:   res = arg1[arg2]
     *   "[]="        escritura indexada: arg1[arg2] = res
     *   "concat"     union de textos
     *   "imprimir"   salida de arg1
     *   "leer"       entrada hacia res
     */
    public String operador;

    public String arg1;
    public String arg2;
    public String resultado;

    /** La misma instruccion escrita como se leeria: "t1 = B * C". */
    public String comentario;

    public Cuadruplo(int indice, String operador, String arg1, String arg2,
                     String resultado, String comentario) {
        this.indice = indice;
        this.operador = operador;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
        this.comentario = comentario;
    }

    public String toString() {
        return indice + ": (" + operador + ", " + arg1 + ", " + arg2 + ", " + resultado + ")";
    }
}
