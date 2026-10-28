// Archivo: OptimizacionBucles.java
//
// F4. Saca de los bucles el codigo invariante y reduce la fuerza de las
// operaciones con variables de induccion.
//
// Pendiente: en la Fase 0 solo existe el esqueleto y no transforma nada.
package backend.optimizacion;

final class OptimizacionBucles implements Fase {

    public String clave() {
        return "F4";
    }

    public String nombre() {
        return "Optimizacion de bucles";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        return false;
    }
}
