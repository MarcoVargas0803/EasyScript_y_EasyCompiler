
package backend;

public class Cuadruplo {

    /** Numero de instruccion. Es la direccion a la que apuntan los saltos. */
    public int indice;

    /**
     * Operador. Ademas de los del lenguaje se usan estos internos:
     *
     * "=" copia: res = arg1
     * "ampliar" conversion ENT->DEC
     * "a_texto" conversion de cualquier valor a TXT, antes de un concat
     * "etiqueta" destino de salto (no ejecuta nada)
     * "ir_a" salto incondicional a res
     * "si_falso" salta a res si arg1 es falso
     * "si_verdadero" salta a res si arg1 es cierto
     * "si_igual" salta a res si arg1 == arg2 (pruebas del SEGUN)
     * "si_>", "si_<=", ... compara y salta en una sola instruccion:
     * salta a res si arg1 <relop> arg2
     * "=[]" lectura indexada: res = arg1[arg2]
     * "[]=" escritura indexada: arg1[arg2] = res
     * "concat" union de textos
     * "imprimir" salida de arg1
     * "leer" entrada hacia res
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
