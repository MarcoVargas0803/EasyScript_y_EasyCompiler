// Archivo: PlegadoConstantes.java
//
// F1. Evalua al compilar las operaciones cuyos operandos ya se conocen y
// propaga los valores constantes dentro de cada bloque basico (Tabla 7.1).
//
// Pendiente: en la Fase 0 solo existe el esqueleto y no transforma nada.
package backend.optimizacion;

final class PlegadoConstantes implements Fase {

    public String clave() {
        return "F1";
    }

    public String nombre() {
        return "Ejecucion en tiempo de compilacion";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        return false;
    }
}
