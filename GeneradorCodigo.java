// Archivo: GeneradorCodigo.java
//
// El esquema de traduccion (tema 1.5): convierte el programa en codigo de tres
// direcciones (Aho, Compiladores 2a ed., seccion 6.2).
//
// Que hace y que NO hace
// ----------------------
// Solo sabe emitir instrucciones y repartir nombres nuevos (temporales y
// etiquetas). No recorre el arbol, no consulta tipos y no decide nada: de eso
// se encarga AnalizadorSemantico, que va llamando aqui conforme baja por el
// arbol. Esa separacion es la que permite que el generador quepa en una pagina.
//
// Por que la traduccion va en la MISMA pasada que la comprobacion de tipos
// -----------------------------------------------------------------------
// Porque necesita exactamente lo que esa pasada acaba de calcular: el tipo de
// cada operando (para saber si hay que insertar una conversion) y su "lugar"
// (donde vive el valor). Hacerlo en una tercera pasada obligaria a recalcular
// los tipos o a guardarlos en el arbol solo para volverlos a leer.
//
// Un detalle sobre los temporales
// -------------------------------
// NO se instalan en la tabla de simbolos: aparecerian en la tabla impresa y
// cambiarian el tamano del marco de datos, y la consola tiene que quedarse
// como esta. Pero la fase siguiente necesita saber donde vive cada uno, asi
// que al terminar la traduccion reciben una direccion propia, a continuacion
// de los datos del programa (ver asignarDireccionesTemporales).
//
// Tampoco se reutilizan: cada valor intermedio tiene su nombre y su sitio.
// Aho lo recomienda asi para el codigo intermedio (seccion 6.2.1): combinar
// temporales es trabajo de la asignacion de registros (seccion 8.8), que
// pertenece a la generacion de codigo objeto.
import java.util.ArrayList;
import java.util.List;

public class GeneradorCodigo {

    /**
     * Destino de un salto que todavia no se conoce.
     *
     * Se escribe en el cuadruplo y se sustituye mas tarde con completar().
     * Se deja visible a proposito: si al terminar queda alguno, el codigo tiene
     * un salto a ninguna parte y etiquetasRotas() lo denuncia.
     */
    public static final String PENDIENTE = "_";

    private static final List<Cuadruplo> codigo = new ArrayList<Cuadruplo>();

    private static int contadorTemporales = 0;
    private static int contadorEtiquetas = 0;

    /** Tipo de cada temporal creado: el de t1 en la posicion 0, y asi. */
    private static final List<String> tiposDeTemporales = new ArrayList<String>();

    /** Direccion de cada temporal, en el mismo orden. Vacia hasta asignarlas. */
    private static final List<Integer> direccionesDeTemporales = new ArrayList<Integer>();

    // ------------------------------------------------------------------
    // Nombres nuevos
    // ------------------------------------------------------------------

    /**
     * Nombre para un valor intermedio: t1, t2, ...
     *
     * Cada operacion binaria produce uno. Son los que convierten un arbol de
     * expresiones en una secuencia lineal de instrucciones.
     */
    public static String nuevoTemporal(Tipo tipo) {
        contadorTemporales++;
        tiposDeTemporales.add(tipo == null ? Tipo.ERROR.lexema : tipo.lexema);
        return "t" + contadorTemporales;
    }

    /** Nombre para un destino de salto: L1, L2, ... */
    public static String nuevaEtiqueta() {
        contadorEtiquetas++;
        return "L" + contadorEtiquetas;
    }

    // ------------------------------------------------------------------
    // Emision
    // ------------------------------------------------------------------

    /** Emite una instruccion y devuelve su indice, que es a donde se salta. */
    public static int emitir(String operador, String arg1, String arg2, String resultado) {
        return emitir(operador, arg1, arg2, resultado, legible(operador, arg1, arg2, resultado));
    }

    public static int emitir(String operador, String arg1, String arg2,
                             String resultado, String comentario) {
        int indice = codigo.size();
        codigo.add(new Cuadruplo(indice, operador, texto(arg1), texto(arg2),
                                 texto(resultado), comentario));
        return indice;
    }

    /** Marca en el codigo el punto al que apunta una etiqueta. */
    public static void etiquetar(String etiqueta) {
        emitir("etiqueta", "-", "-", etiqueta, etiqueta + ":");
    }

    /**
     * Emite una operacion binaria sobre dos operandos ya calculados y devuelve
     * el temporal donde queda el resultado.
     *
     * Las conversiones se emiten antes, de forma explicita. Aho lo llama
     * insertar un operador de conversion (seccion 6.5.2): el codigo intermedio
     * no debe tener conversiones implicitas escondidas, porque quien lo traduzca
     * despues no tiene forma de adivinarlas. Hay tres casos:
     *
     *   - el resultado es DEC y un operando es ENT:       ampliar ese operando
     *   - comparacion entre ENT y DEC (resultado BOOL):   ampliar el ENT, porque
     *     la comparacion se hace en DEC aunque el resultado no sea un numero
     *   - concatenacion con un operando que no es TXT:    a_texto ese operando
     */
    public static String emitirBinaria(String operador, Atributo izq, Atributo der, Tipo resultado) {
        String a = izq.lugar;
        String b = der.lugar;

        boolean mixta = (izq.tipo == Tipo.ENT && der.tipo == Tipo.DEC)
                     || (izq.tipo == Tipo.DEC && der.tipo == Tipo.ENT);
        if (resultado == Tipo.DEC || (resultado == Tipo.BOOL && mixta)) {
            if (izq.tipo == Tipo.ENT) a = emitirConversion(a, Tipo.DEC);
            if (der.tipo == Tipo.ENT) b = emitirConversion(b, Tipo.DEC);
        }

        // La suma con texto no es una suma: es otra operacion, y el codigo
        // intermedio debe decirlo con su propio operador. Sus dos operandos
        // tienen que ser texto ya: el numero que se pega a un mensaje se
        // convierte antes, a la vista. (Un operando con ERROR no se convierte:
        // ya hubo un error y este codigo no se va a usar.)
        String op = operador;
        if (CuboSemantico.esConcatenacion(operador, izq.tipo, der.tipo)) {
            op = "concat";
            if (izq.tipo != Tipo.TXT && !izq.esError()) a = emitirATexto(a);
            if (der.tipo != Tipo.TXT && !der.esError()) b = emitirATexto(b);
        }

        String t = nuevoTemporal(resultado);
        emitir(op, a, b, t);
        return t;
    }

    /** Emite la conversion de un valor cualquiera a TXT y devuelve el temporal. */
    public static String emitirATexto(String origen) {
        String t = nuevoTemporal(Tipo.TXT);
        emitir("a_texto", origen, "-", t);
        return t;
    }

    /** Emite un operador unario y devuelve el temporal con el resultado. */
    public static String emitirUnaria(String operador, Atributo operando, Tipo resultado) {
        String t = nuevoTemporal(resultado);
        emitir(operador, operando.lugar, "-", t);
        return t;
    }

    /** Emite la conversion ENT -> DEC y devuelve el temporal convertido. */
    public static String emitirConversion(String origen, Tipo destino) {
        String t = nuevoTemporal(destino);
        emitir("ampliar", origen, "-", t);
        return t;
    }

    /**
     * Emite una asignacion, insertando la conversion si el destino es DEC y el
     * origen ENT.
     */
    public static void emitirAsignacion(String destino, Atributo origen, Tipo tipoDestino) {
        emitir("=", valorPara(origen, tipoDestino), "-", destino);
    }

    /**
     * Escritura en un elemento: base[desplazamiento] = valor, con la misma
     * conversion que una asignacion corriente. Que el destino sea un elemento
     * no cambia su tipo: un arreglo DEC guarda DEC aunque se le de un 1.
     */
    public static void emitirEscrituraIndexada(String base, String desplazamiento,
                                               Atributo origen, Tipo tipoElemento) {
        emitir("[]=", base, desplazamiento, valorPara(origen, tipoElemento));
    }

    /** El lugar del origen, ampliado antes si el destino es DEC y el origen ENT. */
    private static String valorPara(Atributo origen, Tipo tipoDestino) {
        if (tipoDestino == Tipo.DEC && origen.tipo == Tipo.ENT) {
            return emitirConversion(origen.lugar, Tipo.DEC);
        }
        return origen.lugar;
    }

    /**
     * La constante 1 que suma o resta un X++ / X--, escrita en el tipo de X.
     *
     * X++ sobre un DEC es X = X + 1.0: con un 1 entero la suma mezclaria tipos
     * sin conversion, justo lo que emitirBinaria evita en el resto del codigo.
     * Como el literal se conoce al compilar, se escribe ya en DEC en vez de
     * emitir un ampliar en cada vuelta.
     */
    public static String unoDelTipo(Tipo tipo) {
        return (tipo == Tipo.DEC) ? "1.0" : "1";
    }

    /**
     * Calcula la direccion de un elemento y devuelve el temporal que la
     * contiene (Aho, seccion 6.4.3).
     *
     * Para un arreglo:  desplazamiento = i * ancho
     * Para una matriz:  desplazamiento = (i * columnas + j) * ancho
     *
     * Se multiplica por el ancho del elemento porque las direcciones se cuentan
     * en bytes, no en posiciones: el tercer elemento de un arreglo de DEC esta
     * 24 bytes mas adelante, no 3.
     */
    public static String emitirDesplazamiento(Simbolo s, Atributo indiceFila, Atributo indiceColumna) {
        String base = indiceFila.lugar;

        if (indiceColumna != null) {
            String t1 = nuevoTemporal(Tipo.ENT);
            emitir("*", base, String.valueOf(s.columnas), t1);
            String t2 = nuevoTemporal(Tipo.ENT);
            emitir("+", t1, indiceColumna.lugar, t2);
            base = t2;
        }

        String t = nuevoTemporal(Tipo.ENT);
        emitir("*", base, String.valueOf(s.anchoElemento), t);
        return t;
    }

    // ------------------------------------------------------------------
    // Backpatching (Aho, Compiladores 2a ed., seccion 6.7)
    //
    // Un salto hacia adelante no puede escribir su destino cuando se emite: el
    // sitio al que va todavia no existe. La solucion es emitirlo con el destino
    // en blanco, apuntar su numero de instruccion en una lista, y rellenar la
    // lista entera cuando el destino se conozca.
    // ------------------------------------------------------------------

    /** Lista con una sola instruccion pendiente. */
    public static List<Integer> nuevaLista(int indice) {
        List<Integer> l = new ArrayList<Integer>();
        l.add(Integer.valueOf(indice));
        return l;
    }

    /** Une dos listas de pendientes en una sola. */
    public static List<Integer> unir(List<Integer> a, List<Integer> b) {
        List<Integer> l = new ArrayList<Integer>();
        if (a != null) l.addAll(a);
        if (b != null) l.addAll(b);
        return l;
    }

    /**
     * Rellena el destino de todas las instrucciones de la lista.
     *
     * Es la operacion que da nombre a la tecnica: se vuelve atras sobre codigo
     * ya emitido y se le completa el hueco que quedo abierto.
     */
    public static void completar(List<Integer> lista, String etiqueta) {
        if (lista == null) {
            return;
        }
        for (int i = 0; i < lista.size(); i++) {
            int indice = lista.get(i).intValue();
            if (indice < 0 || indice >= codigo.size()) {
                continue;
            }
            Cuadruplo c = codigo.get(indice);
            c.resultado = etiqueta;
            c.comentario = legible(c.operador, c.arg1, c.arg2, c.resultado);
        }
    }

    /**
     * Devuelve una etiqueta colocada en el punto actual del codigo.
     *
     * Es el marcador que Aho escribe como M en sus reglas: sirve para capturar
     * "donde estamos ahora" y poder apuntar saltos a este sitio.
     *
     * Si la ultima instruccion ya es una etiqueta, este punto ya tiene nombre y
     * se reutiliza: dos etiquetas seguidas marcan el mismo sitio, y la segunda
     * solo anade ruido (por ejemplo, el final de un SI anidado que coincide con
     * el final del SI de fuera).
     */
    public static String etiquetaAqui() {
        if (!codigo.isEmpty()) {
            Cuadruplo ultimo = codigo.get(codigo.size() - 1);
            if ("etiqueta".equals(ultimo.operador)) {
                return ultimo.resultado;
            }
        }
        String e = nuevaEtiqueta();
        etiquetar(e);
        return e;
    }

    /**
     * Rellena la lista con el punto actual, creando la etiqueta SOLO si hay
     * algun salto que la necesite.
     *
     * Con el codigo de paso (Aho, seccion 6.6.5) muchas listas quedan vacias:
     * el camino que "cae" hacia la instruccion siguiente no salta, y una
     * etiqueta a la que nadie salta no sirve de nada.
     */
    public static void completarAqui(List<Integer> lista) {
        if (lista == null || lista.isEmpty()) {
            return;
        }
        completar(lista, etiquetaAqui());
    }

    /**
     * Comparacion y salto en UNA instruccion (Aho, figura 6.37): en vez de
     *
     *     t1 = A > 1
     *     si t1 ir_a L
     *
     * se emite  si A > 1 ir_a L.  El destino queda pendiente.
     *
     * Con siCierta = false el salto se da cuando la comparacion es FALSA, y
     * para eso se usa el relacional complementario: saltar si no es A > 1 es
     * saltar si A <= 1. Es lo que permite que el cuerpo de un SI quede justo
     * despues de la condicion, sin un salto que lo esquive.
     *
     * Las conversiones son las mismas que en emitirBinaria: si se compara un
     * ENT con un DEC, el ENT se amplia antes.
     */
    public static int emitirSaltoRelacional(String relop, Atributo izq, Atributo der, boolean siCierta) {
        String a = izq.lugar;
        String b = der.lugar;
        boolean mixta = (izq.tipo == Tipo.ENT && der.tipo == Tipo.DEC)
                     || (izq.tipo == Tipo.DEC && der.tipo == Tipo.ENT);
        if (mixta) {
            if (izq.tipo == Tipo.ENT) a = emitirConversion(a, Tipo.DEC);
            if (der.tipo == Tipo.ENT) b = emitirConversion(b, Tipo.DEC);
        }
        String op = siCierta ? relop : complemento(relop);
        return emitir("si_" + op, a, b, PENDIENTE);
    }

    /** El relacional que es cierto exactamente cuando el dado es falso. */
    public static String complemento(String relop) {
        if (">".equals(relop))  return "<=";
        if ("<=".equals(relop)) return ">";
        if ("<".equals(relop))  return ">=";
        if (">=".equals(relop)) return "<";
        if ("==".equals(relop)) return "!=";
        if ("!=".equals(relop)) return "==";
        return relop;
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    public static List<Cuadruplo> obtenerCodigo() {
        return codigo;
    }

    public static int cantidad() {
        return codigo.size();
    }

    public static int siguienteIndice() {
        return codigo.size();
    }

    /**
     * Comprobacion de consistencia: todo salto debe apuntar a una etiqueta que
     * exista. Devuelve la lista de etiquetas rotas, vacia si todo esta bien.
     *
     * No es una regla del lenguaje sino una prueba del propio generador: si
     * falla, el error esta en el esquema de traduccion, no en el programa del
     * usuario.
     */
    public static List<String> etiquetasRotas() {
        List<String> definidas = new ArrayList<String>();
        for (int i = 0; i < codigo.size(); i++) {
            if ("etiqueta".equals(codigo.get(i).operador)) {
                definidas.add(codigo.get(i).resultado);
            }
        }
        List<String> rotas = new ArrayList<String>();
        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            boolean esSalto = "ir_a".equals(c.operador)
                           || c.operador.startsWith("si_");
            if (esSalto && !definidas.contains(c.resultado) && !rotas.contains(c.resultado)) {
                rotas.add(c.resultado);
            }
        }
        return rotas;
    }

    // ------------------------------------------------------------------
    // Direcciones de los temporales (Aho, secciones 6.3.4 y 7.2)
    // ------------------------------------------------------------------

    /**
     * Da direccion a todos los temporales, uno detras de otro a partir de
     * 'base', con el ancho de su tipo: es la misma regla que sigue la tabla de
     * simbolos con las variables (direccion = base + lo ya ocupado).
     *
     * Se llama con base = TablaSimbolos.tamanoDelMarco(), asi los temporales
     * quedan en el registro de activacion justo despues de los datos locales,
     * que es donde Aho los coloca (figura 7.5). Devuelve el primer byte libre
     * despues del ultimo temporal.
     *
     * Un temporal de tipo ERROR (solo existen cuando el programa tiene
     * errores) tiene ancho 0 y no ocupa memoria, igual que en la tabla de
     * simbolos.
     */
    public static int asignarDireccionesTemporales(int base) {
        direccionesDeTemporales.clear();
        int siguiente = base;
        for (int i = 0; i < tiposDeTemporales.size(); i++) {
            direccionesDeTemporales.add(Integer.valueOf(siguiente));
            siguiente += anchoDeTemporal(i);
        }
        return siguiente;
    }

    public static int cantidadDeTemporales() {
        return tiposDeTemporales.size();
    }

    /** Nombre del temporal i (0 es t1). */
    public static String nombreDeTemporal(int i) {
        return "t" + (i + 1);
    }

    public static String tipoDeTemporal(int i) {
        return tiposDeTemporales.get(i);
    }

    public static int anchoDeTemporal(int i) {
        return Tipo.desdeLexema(tiposDeTemporales.get(i)).ancho;
    }

    /** Direccion del temporal i, o -1 si todavia no se asignaron. */
    public static int direccionDeTemporal(int i) {
        return (i < direccionesDeTemporales.size()) ? direccionesDeTemporales.get(i).intValue() : -1;
    }

    public static void reiniciar() {
        codigo.clear();
        tiposDeTemporales.clear();
        direccionesDeTemporales.clear();
        contadorTemporales = 0;
        contadorEtiquetas = 0;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private static String texto(String s) {
        return (s == null) ? "-" : s;
    }

    /** Escribe la instruccion como se leeria en pseudocodigo. */
    private static String legible(String op, String a1, String a2, String res) {
        if ("etiqueta".equals(op)) return res + ":";
        if ("ir_a".equals(op))     return "ir_a " + res;
        if ("si_falso".equals(op))    return "si_falso " + a1 + " ir_a " + res;
        if ("si_verdadero".equals(op)) return "si " + a1 + " ir_a " + res;
        // si_>  si_<=  si_==  ...: comparacion y salto en una sola instruccion,
        // que es como lo hace una maquina de verdad.
        if (op.startsWith("si_") && !"si_igual".equals(op)) {
            return "si " + a1 + " " + op.substring(3) + " " + a2 + " ir_a " + res;
        }
        if ("si_igual".equals(op)) return "si " + a1 + " == " + a2 + " ir_a " + res;
        if ("=".equals(op))        return res + " = " + a1;
        if ("ampliar".equals(op))  return res + " = (DEC) " + a1;
        if ("a_texto".equals(op))  return res + " = (TXT) " + a1;
        if ("=[]".equals(op))      return res + " = " + a1 + "[" + a2 + "]";
        if ("[]=".equals(op))      return a1 + "[" + a2 + "] = " + res;
        if ("imprimir".equals(op)) return "imprimir " + a1;
        if ("leer".equals(op))     return "leer " + res;
        if ("concat".equals(op))   return res + " = " + a1 + " . " + a2;
        // Los unarios llevan sufijo interno para no confundirse con el binario
        // del mismo simbolo, pero al leerlos se escriben como se escribieron.
        if ("-u".equals(op))       return res + " = -" + a1;
        if ("!!".equals(op))       return res + " = !!" + a1;
        if (a2 == null || "-".equals(a2)) return res + " = " + op + " " + a1;
        return res + " = " + a1 + " " + op + " " + a2;
    }
}
