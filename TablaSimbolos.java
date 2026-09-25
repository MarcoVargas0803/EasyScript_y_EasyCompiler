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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Bloque -> bloque que lo encierra. La pila de arriba solo sabe que hay
    // abierto AHORA; este mapa conserva la forma del arbol de ambitos cuando la
    // pila ya se vacio, que es lo que hace falta para imprimir la tabla y para
    // cualquier consulta posterior al recorrido.
    private static Map<Integer, Integer> padreDeBloque = new HashMap<Integer, Integer>();

    // Disposicion del almacenamiento (Aho, seccion 6.3.4, Fig. 6.17).
    //
    // Paralelas a pilaAmbitos: para el bloque abierto, donde empieza en memoria
    // y cuanto lleva ocupado. Un bloque interior arranca donde termina lo que su
    // padre lleva reservado; dos bloques HERMANOS arrancan en el mismo sitio,
    // porque no estan vivos a la vez y pueden reutilizar el espacio.
    private static List<Integer> pilaBases = new ArrayList<Integer>();
    private static List<Integer> pilaDesplazamientos = new ArrayList<Integer>();

    // ------------------------------------------------------------------
    // Manejo de ambitos
    // ------------------------------------------------------------------
    public static void abrirAmbito() {
        int padre = bloqueActual();          // 0 cuando no hay ninguno abierto
        int base = pilaAmbitos.isEmpty() ? 0 : baseActual() + desplazamientoActual();
        contadorBloques++;
        padreDeBloque.put(Integer.valueOf(contadorBloques), Integer.valueOf(padre));
        pilaAmbitos.add(Integer.valueOf(contadorBloques));
        pilaBases.add(Integer.valueOf(base));
        pilaDesplazamientos.add(Integer.valueOf(0));
    }

    /** Byte donde empieza el bloque abierto. */
    public static int baseActual() {
        if (pilaBases.isEmpty()) return 0;
        return pilaBases.get(pilaBases.size() - 1).intValue();
    }

    /** Bytes ya reservados dentro del bloque abierto. */
    public static int desplazamientoActual() {
        if (pilaDesplazamientos.isEmpty()) return 0;
        return pilaDesplazamientos.get(pilaDesplazamientos.size() - 1).intValue();
    }

    /**
     * Reserva espacio para un simbolo y le asigna su direccion.
     *
     * Va aparte de agregarTipo porque el tamano de un arreglo o una matriz se
     * conoce despues: hay que evaluar la expresion de los corchetes, y eso lo
     * hace el analizador semantico. Quien llama debe haber dejado ya 'filas' y
     * 'columnas' puestas.
     */
    public static void asignarDireccion(Simbolo s) {
        if (s == null || "INDEFINIDO".equals(s.categoria)) {
            return;   // un hueco de error no ocupa memoria
        }
        s.anchoElemento = Tipo.desdeLexema(s.tipo).ancho;

        int elementos = 1;
        if ("ARREGLO".equals(s.categoria)) {
            elementos = s.filas;
        } else if ("MATRIZ".equals(s.categoria)) {
            elementos = s.filas * s.columnas;
        }
        // Si el tamano no se pudo determinar (ya se reporto como error) se
        // reserva un elemento, para que la tabla siga siendo legible.
        if (elementos <= 0) {
            elementos = 1;
        }

        s.ancho = s.anchoElemento * elementos;
        s.desplazamiento = desplazamientoActual();
        s.direccion = baseActual() + s.desplazamiento;

        if (!pilaDesplazamientos.isEmpty()) {
            pilaDesplazamientos.set(pilaDesplazamientos.size() - 1,
                Integer.valueOf(s.desplazamiento + s.ancho));
        }
    }

    /**
     * Tamano del marco de activacion: el byte mas alto que alguna declaracion
     * llego a ocupar. Es cuanta memoria necesita el programa para sus datos.
     */
    public static int tamanoDelMarco() {
        int max = 0;
        for (int i = 0; i < tabla.size(); i++) {
            Simbolo s = tabla.get(i);
            int fin = s.direccion + s.ancho;
            if (fin > max) max = fin;
        }
        return max;
    }

    /** Bloque que encierra al indicado; 0 si es el mas externo. */
    public static int padreDe(int bloque) {
        Integer p = padreDeBloque.get(Integer.valueOf(bloque));
        return (p == null) ? 0 : p.intValue();
    }

    public static void cerrarAmbito() {
        if (!pilaAmbitos.isEmpty()) {
            pilaAmbitos.remove(pilaAmbitos.size() - 1);
        }
        // El padre NO hereda lo que gasto el hijo: al cerrarse el bloque ese
        // espacio queda libre para el siguiente bloque hermano.
        if (!pilaBases.isEmpty()) {
            pilaBases.remove(pilaBases.size() - 1);
        }
        if (!pilaDesplazamientos.isEmpty()) {
            pilaDesplazamientos.remove(pilaDesplazamientos.size() - 1);
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

        // Un INDEFINIDO no es una declaracion: es el hueco que dejo un nombre
        // usado antes de declararse, puesto ahi para no repetir el mismo error
        // en cada uso. Si mas adelante aparece la declaracion de verdad, ocupa
        // su lugar; tratarlo como duplicado seria denunciar un error que el
        // propio compilador invento.
        if (previo != null && "INDEFINIDO".equals(previo.categoria)
                && !"INDEFINIDO".equals(categoria)) {
            tabla.remove(previo);
            previo = null;
        }

        if (previo != null) {
            EasyCompiler.listaErrores.add(new ErrorCompilador("Semántico", "SEM-01", id.beginLine, id.beginColumn,
                "El identificador '" + nombre + "' ya fue declarado en este ámbito (línea "
                    + previo.linea + ", columna " + previo.columna + ").",
                "Cada nombre puede declararse una sola vez por bloque. Renombra la segunda declaración o elimínala."));
            return previo;
        }

        Simbolo s = new Simbolo(nombre, tipo, categoria, dimensiones,
                                nivelActual(), bloqueActual(),
                                inicializada, valor, id.beginLine, id.beginColumn);
        s.bloquePadre = padreDe(bloqueActual());
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
    /**
     * Simbolos declarados por el programa.
     *
     * Deja fuera los INDEFINIDO: no son declaraciones del programador sino
     * marcadores internos para no repetir el mismo error. Sacarlos en la tabla
     * haria parecer que el programa declaro algo de tipo ERROR.
     */
    public static List<Simbolo> obtenerTodos() {
        List<Simbolo> res = new ArrayList<Simbolo>();
        for (int i = 0; i < tabla.size(); i++) {
            if (!"INDEFINIDO".equals(tabla.get(i).categoria)) {
                res.add(tabla.get(i));
            }
        }
        return res;
    }

    public static boolean estaVacia() {
        return obtenerTodos().isEmpty();
    }

    // ------------------------------------------------------------------
    // Reinicio
    //
    // Toda esta clase es estatica, asi que su estado sobrevive al analisis de
    // un archivo. Mientras el compilador se ejecute una vez por proceso da
    // igual, pero en cuanto se analicen dos archivos seguidos (o se le ponga
    // una interfaz grafica) la tabla saldria con los simbolos del anterior y
    // la numeracion de bloques continuaria donde se quedo. Es el punto 9 de
    // plan_correcciones.md, y la fase semantica ya lo necesita.
    // ------------------------------------------------------------------
    public static void reiniciar() {
        tabla.clear();
        pilaAmbitos.clear();
        pilaBases.clear();
        pilaDesplazamientos.clear();
        padreDeBloque.clear();
        contadorBloques = 0;
    }

    // ------------------------------------------------------------------
    // Consultas que necesita el analizador semantico
    // ------------------------------------------------------------------

    /**
     * Marca como usado el simbolo visible con ese nombre. Sirve para avisar
     * al final de "declaraste esto y nunca lo usaste", que casi siempre es una
     * variable mal escrita en el punto de uso.
     */
    public static void marcarUsado(String nombre) {
        Simbolo s = buscar(nombre);
        if (s != null) {
            s.usado = true;
        }
    }

    /** Simbolos declarados directamente en ese bloque, en orden de aparicion. */
    public static List<Simbolo> simbolosDe(int bloque) {
        List<Simbolo> res = new ArrayList<Simbolo>();
        for (int i = 0; i < tabla.size(); i++) {
            if (tabla.get(i).bloque == bloque) {
                res.add(tabla.get(i));
            }
        }
        return res;
    }
}
