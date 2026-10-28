// Archivo: ReordenacionOperaciones.java
//
// F3. Ordena los operandos de los operadores conmutativos en forma canonica,
// reasocia constantes enteras y aplica identidades algebraicas.
//
// Pendiente: en la Fase 0 solo existe el esqueleto y no transforma nada.
package backend.optimizacion;

final class ReordenacionOperaciones implements Fase {

    public String clave() {
        return "F3";
    }

    public String nombre() {
        return "Reordenacion de operaciones";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        return false;
    }
}
