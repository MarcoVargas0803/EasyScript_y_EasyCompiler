// Archivo: EliminacionRedundancias.java
//
// F2. Detecta cuadruplos equivalentes a otro anterior del mismo bloque
// (mismos operandos con las mismas dependencias) y reutiliza su resultado.
//
// Pendiente: en la Fase 0 solo existe el esqueleto y no transforma nada.
package backend.optimizacion;

final class EliminacionRedundancias implements Fase {

    public String clave() {
        return "F2";
    }

    public String nombre() {
        return "Eliminacion de redundancias";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        return false;
    }
}
