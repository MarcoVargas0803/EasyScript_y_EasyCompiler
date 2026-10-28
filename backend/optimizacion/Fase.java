// Archivo: Fase.java
//
// Una fase del optimizador: recibe el programa, lo transforma en su lugar,
// anota cada cambio en la bitacora y dice si cambio algo. El orquestador
// repite las fases mientras alguna siga cambiando el codigo.
package backend.optimizacion;

interface Fase {

    /** "F1", "F2", ... */
    String clave();

    /** Nombre para mostrar. */
    String nombre();

    /** Aplica la fase. Devuelve true si modifico el codigo. */
    boolean aplicar(ProgramaIntermedio programa, Bitacora bitacora);
}
