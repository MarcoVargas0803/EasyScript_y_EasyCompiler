// Archivo: OptimizacionRegiones.java
//
// F5. Recorre las regiones del grafo de flujo (bucles de dentro hacia fuera y
// el programa entero) con los vectores A/R/B, y optimiza entre bloques.
//
// Pendiente: en la Fase 0 solo existe el esqueleto y no transforma nada.
package backend.optimizacion;

final class OptimizacionRegiones implements Fase {

    public String clave() {
        return "F5";
    }

    public String nombre() {
        return "Optimizacion de regiones";
    }

    public boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora) {
        return false;
    }
}
