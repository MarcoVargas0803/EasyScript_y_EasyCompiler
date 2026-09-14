// Archivo: TablaSimbolos.java
//
// Tabla de simbolos de EasyScript.
//
// Se construye siguiendo el Ejemplo 5.10 / Figura 5.8 de Aho (Compiladores,
// 2a ed., pags. 315-316), donde la declaracion "D -> T L" se traduce con:
//
//      T -> int     { T.tipo = integer }       <- atributo SINTETIZADO
//      D -> T L     { L.her  = T.tipo }        <- se HEREDA hacia el identificador
//      L -> id      { agregarTipo(id.entrada, L.her) }
//
// El metodo agregarTipo() de esta clase es exactamente la funcion que el autor
// deja como supuesta: "Suponemos que la funcion agregarTipo instala en forma
// apropiada el tipo L.her como el tipo del identificador representado" (pag. 316).
//
// El autor tambien advierte ahi mismo: "Esta definicion dirigida por la sintaxis
// no verifica si un identificador se declara mas de una vez; puede modificarse
// para ello." Esa modificacion es la que hacemos abajo: agregarTipo detecta el
// duplicado y lo reporta en la tabla de errores como error Semantico.
//
// La insercion es un "efecto adicional controlado" en el sentido de la seccion
// 5.2.5 (pag. 314): no devuelve un valor que otra regla consuma, sino que
// modifica una estructura global. Como cada identificador ocupa su propia
// entrada, el orden de las inserciones no altera el resultado.
import java.util.ArrayList;
import java.util.List;

public class TablaSimbolos {

    // La tabla propiamente dicha. Se conservan TODAS las entradas, incluso las de
    // ambitos ya cerrados, porque el proposito de esta fase es poder imprimirla
    // completa al final del analisis.
    private static List<Simbolo> tabla = new ArrayList<Simbolo>();

    // Pila de ambitos abiertos. Cada elemento es el identificador unico del
    // bloque correspondiente; el ultimo es el bloque en el que estamos parados.
    // Se usa una pila y no un simple contador de profundidad porque dos bloques
    // hermanos (el SI y el SINO de un mismo condicional) tienen la misma
    // profundidad pero son ambitos distintos.
    private static List<Integer> pilaAmbitos = new ArrayList<Integer>();

    // Numerador de bloques: cada bloque que se abre recibe un id nuevo.
    private static int contadorBloques = 0;

    // ------------------------------------------------------------------
    // Manejo de ambitos
    // ------------------------------------------------------------------
    public static void abrirAmbito() {
        contadorBloques++;
        pilaAmbitos.add(Integer.valueOf(contadorBloques));
    }

    public static void cerrarAmbito() {
        if (!pilaAmbitos.isEmpty()) {
            pilaAmbitos.remove(pilaAmbitos.size() - 1);
        }
    }

    /** Profundidad de anidamiento actual: el cuerpo de INICIO{} es el nivel 1. */
    public static int nivelActual() {
        return pilaAmbitos.size();
    }

    /** Identificador del bloque concreto en el que estamos declarando. */
    public static int bloqueActual() {
        if (pilaAmbitos.isEmpty()) return 0;
        return pilaAmbitos.get(pilaAmbitos.size() - 1).intValue();
    }

    // ------------------------------------------------------------------
    // agregarTipo: la accion semantica de la Figura 5.8.
    //
    //   id    -> id.entrada (el Token que el parser capturo)
    //   tipo  -> L.her      (el tipo heredado que bajo desde TipoDato())
    //
    // Devuelve el simbolo instalado, o el simbolo previo si el nombre ya
    // existia en este mismo bloque.
    // ------------------------------------------------------------------
    public static Simbolo agregarTipo(Token id, String tipo, String categoria,
                                      String dimensiones, boolean inicializada, String valor) {
        if (id == null) {
            return null;
        }

        String nombre = id.image;

        // Verificacion de doble declaracion (la modificacion que sugiere el autor).
        // Solo cuenta como duplicado si el nombre ya existe en ESTE mismo bloque;
        // repetirlo en un bloque interior es ocultamiento (shadowing), no error.
        Simbolo previo = buscarEnBloque(nombre, bloqueActual());
        if (previo != null) {
            EasyCompiler.listaErrores.add(new ErrorCompilador("Semántico", id.beginLine, id.beginColumn,
                "El identificador '" + nombre + "' ya fue declarado en este ámbito (línea "
                    + previo.linea + ", columna " + previo.columna + ").",
                "Cada nombre puede declararse una sola vez por bloque. Renombra la segunda declaración o elimínala."));
            return previo;
        }

        Simbolo s = new Simbolo(nombre, tipo, categoria, dimensiones,
                                nivelActual(), bloqueActual(),
                                inicializada, valor, id.beginLine, id.beginColumn);
        tabla.add(s);
        return s;
    }

    // ------------------------------------------------------------------
    // Busquedas
    // ------------------------------------------------------------------

    /**
     * Busca el nombre unicamente dentro del bloque indicado. Es la consulta que
     * usa agregarTipo para decidir si hay doble declaracion.
     */
    public static Simbolo buscarEnBloque(String nombre, int bloque) {
        for (int i = 0; i < tabla.size(); i++) {
            Simbolo s = tabla.get(i);
            if (s.bloque == bloque && s.nombre.equals(nombre)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Busca el nombre visible desde el punto actual, recorriendo la pila de
     * ambitos del mas interno al mas externo. Es la consulta que usaran las
     * reglas de "declarar antes de usar" y de compatibilidad de tipos.
     */
    public static Simbolo buscar(String nombre) {
        for (int i = pilaAmbitos.size() - 1; i >= 0; i--) {
            Simbolo s = buscarEnBloque(nombre, pilaAmbitos.get(i).intValue());
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Acceso para la impresion final
    // ------------------------------------------------------------------
    public static List<Simbolo> obtenerTodos() {
        return tabla;
    }

    public static boolean estaVacia() {
        return tabla.isEmpty();
    }
}
