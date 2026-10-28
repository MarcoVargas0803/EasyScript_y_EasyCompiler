// Archivo: Bitacora.java
//
// Registro de cada transformacion que hace el optimizador: en que fase, con que
// regla y como quedo la instruccion. Es lo que muestra --bitacora-optimizacion.
//
// Toda transformacion pasa por registrar(): para depurar una fase basta con un
// punto de interrupcion ahi y mirar 'fase', 'regla', 'antes' y 'despues'.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.List;

final class Bitacora {

    static final class Entrada {
        final String fase;
        final String regla;
        final String antes;
        final String despues;

        Entrada(String fase, String regla, String antes, String despues) {
            this.fase = fase;
            this.regla = regla;
            this.antes = antes;
            this.despues = despues;
        }

        public String toString() {
            return "[" + fase + "] " + regla + ": " + antes + "  ->  " + despues;
        }
    }

    private final List<Entrada> entradas = new ArrayList<Entrada>();

    /**
     * Anota una transformacion. 'antes' y 'despues' son la instruccion en forma
     * legible; "(eliminada)" o "(nueva)" cuando una de las dos no existe.
     */
    void registrar(String fase, String regla, String antes, String despues) {
        entradas.add(new Entrada(fase, regla, antes, despues));
    }

    List<Entrada> entradas() {
        return entradas;
    }

    int cantidad(String fase) {
        int n = 0;
        for (Entrada e : entradas) {
            if (e.fase.equals(fase)) {
                n++;
            }
        }
        return n;
    }
}
