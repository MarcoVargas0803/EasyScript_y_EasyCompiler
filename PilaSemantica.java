// Archivo: PilaSemantica.java
//
// La pila semantica (tema 1.4).
//
// Al evaluar una expresion, cada operando se APILA y cada operador DESAPILA los
// suyos, calcula el resultado y lo vuelve a apilar. Al terminar queda un solo
// Atributo: el de la expresion completa. Es el mismo mecanismo que describe Aho
// (Compiladores, 2a ed., seccion 5.5.3) para evaluar una definicion dirigida
// por la sintaxis durante el analisis.
//
// Por que una pila explicita y no las variables locales de cada visit()
// ---------------------------------------------------------------------
// Con recursion se podria devolver el tipo en el "return" de cada visita. Se usa
// una pila de verdad por dos razones practicas:
//
//   1. Un operador n-ario ("A + B + C" es UN nodo con tres hijos y dos
//      operadores) se reduce de izquierda a derecha sin construir nada: apila,
//      reduce, apila, reduce.
//   2. Cuando la recuperacion de errores deja un subarbol a medias, la pila
//      queda con basura. marca()/restaurar() la deja como estaba al empezar la
//      sentencia, y el analisis del resto del programa no se descuadra. Con
//      valores de retorno no habria nada que limpiar... ni forma de saberlo.
//
// Esta clase es solo la pila. Quien decide que tipo sale de "ENT + DEC" es
// CuboSemantico, y quien reporta el error es AnalizadorSemantico.
import java.util.ArrayList;
import java.util.List;

public class PilaSemantica {

    private final List<Atributo> operandos = new ArrayList<Atributo>();

    public void apilar(Atributo a) {
        operandos.add(a);
    }

    /** Saca el operando de la cima. Si esta vacia devuelve un Atributo ERROR. */
    public Atributo desapilar() {
        if (operandos.isEmpty()) {
            return Atributo.error(null);
        }
        return operandos.remove(operandos.size() - 1);
    }

    /** Mira la cima sin sacarla. */
    public Atributo cima() {
        if (operandos.isEmpty()) {
            return Atributo.error(null);
        }
        return operandos.get(operandos.size() - 1);
    }

    public int tamano() {
        return operandos.size();
    }

    public boolean vacia() {
        return operandos.isEmpty();
    }

    // ------------------------------------------------------------------
    // Marca y restauracion
    //
    // Se toma una marca al entrar en cada sentencia y se restaura al salir.
    // Asi, si la sentencia venia rota de la fase sintactica y dejo operandos
    // sueltos, la siguiente empieza con la pila limpia.
    // ------------------------------------------------------------------

    public int marca() {
        return operandos.size();
    }

    public void restaurar(int marca) {
        while (operandos.size() > marca && !operandos.isEmpty()) {
            operandos.remove(operandos.size() - 1);
        }
    }

    public void reiniciar() {
        operandos.clear();
    }

    public String toString() {
        return operandos.toString();
    }
}
