// Archivo: AsignacionesMuertas.java
//
// F6. Elimina las asignaciones cuyo valor nunca se usa y, al final, compacta
// los temporales cuyo tiempo de vida no se solapa.
//
// Pendiente: en la Fase 0 solo existe el esqueleto y no transforma nada.
package backend.optimizacion;

final class AsignacionesMuertas implements Fase {

    public String clave() {
        return "F6";
    }

    public String nombre() {
        return "Asignaciones muertas";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        return false;
    }
}
