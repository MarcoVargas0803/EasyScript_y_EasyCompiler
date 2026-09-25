// Archivo: CuboSemantico.java
//
// La tabla de compatibilidad de tipos (tema 1.3).
//
// Responde a una sola pregunta: dado un operador y los tipos de sus operandos,
// que tipo sale, o si la operacion no tiene sentido. Es la funcion de
// comprobacion de tipos de Aho (Compiladores, 2a ed., seccion 6.5).
//
// Esta escrito como un MAPA y no como una cascada de if a proposito: asi las
// reglas son datos que se pueden recorrer, imprimir y revisar de un vistazo, en
// vez de logica escondida entre condicionales.
//
// Politica de conversiones de EasyScript
// --------------------------------------
//   ENT -> DEC   se permite en silencio (ampliacion: 5 vale igual que 5.0)
//   DEC -> ENT   es ERROR, porque perderia los decimales sin avisar
//   BOOL, LETRA y TXT no se mezclan con numeros
//
// La suma es el unico operador con doble personalidad: entre numeros suma, y en
// cuanto uno de los operandos es TXT concatena. Esa decision se toma AQUI, por
// los tipos, y no por la forma del arbol: la gramatica no puede distinguirlas
// porque ExpresionNivel1 se come todas las sumas antes de que
// ExpresionConcatenada llegue a verlas.
import java.util.HashMap;
import java.util.Map;

public class CuboSemantico {

    /** Asignacion valida y sin conversion. */
    public static final int ASIGNACION_OK = 0;
    /** Asignacion valida, pero el origen se amplia de ENT a DEC. */
    public static final int ASIGNACION_CONVERSION = 1;
    /** Asignacion invalida. */
    public static final int ASIGNACION_ERROR = -1;

    private static final Map<String, Tipo> BINARIOS = new HashMap<String, Tipo>();
    private static final Map<String, Tipo> UNARIOS = new HashMap<String, Tipo>();

    private static final Tipo[] NUMEROS = { Tipo.ENT, Tipo.DEC };
    private static final Tipo[] TODOS = { Tipo.ENT, Tipo.DEC, Tipo.BOOL, Tipo.LETRA, Tipo.TXT };

    static {
        // ---- Aritmeticos entre numeros -------------------------------------
        // ENT con ENT da ENT; en cuanto aparece un DEC el resultado es DEC.
        //
        // Ojo con la division: ENT entre ENT da ENT (division entera). No es un
        // descuido. El archivo finalprueba.txt declara An de tipo ENT y le
        // asigna ((B*N)*(N+1))/2; si la division diera DEC, ese programa (que
        // hoy compila limpio) empezaria a dar error de tipos.
        String[] aritmeticos = { "+", "-", "*", "/", "**" };
        for (int k = 0; k < aritmeticos.length; k++) {
            for (int i = 0; i < NUMEROS.length; i++) {
                for (int j = 0; j < NUMEROS.length; j++) {
                    Tipo a = NUMEROS[i];
                    Tipo b = NUMEROS[j];
                    Tipo r = (a == Tipo.DEC || b == Tipo.DEC) ? Tipo.DEC : Tipo.ENT;
                    poner(BINARIOS, aritmeticos[k], a, b, r);
                }
            }
        }

        // ---- Modulo: solo entre enteros ------------------------------------
        // El residuo esta definido sobre enteros. Un modulo con decimales no
        // tiene una interpretacion unica, asi que se rechaza en vez de inventar
        // una.
        poner(BINARIOS, "%", Tipo.ENT, Tipo.ENT, Tipo.ENT);

        // ---- Concatenacion: suma con algun TXT ------------------------------
        for (int i = 0; i < TODOS.length; i++) {
            poner(BINARIOS, "+", Tipo.TXT, TODOS[i], Tipo.TXT);
            poner(BINARIOS, "+", TODOS[i], Tipo.TXT, Tipo.TXT);
        }

        // ---- Relacionales de orden -----------------------------------------
        // Entre numeros siempre; entre LETRA por su codigo. Dos TXT no se
        // ordenan: decidir si un texto es menor que otro es orden alfabetico, y
        // eso es una decision de biblioteca, no del lenguaje.
        String[] orden = { ">", "<", ">=", "<=" };
        for (int k = 0; k < orden.length; k++) {
            for (int i = 0; i < NUMEROS.length; i++) {
                for (int j = 0; j < NUMEROS.length; j++) {
                    poner(BINARIOS, orden[k], NUMEROS[i], NUMEROS[j], Tipo.BOOL);
                }
            }
            poner(BINARIOS, orden[k], Tipo.LETRA, Tipo.LETRA, Tipo.BOOL);
        }

        // ---- Igualdad -------------------------------------------------------
        // Mas permisiva que el orden: cualquier par del mismo tipo se compara, y
        // ENT con DEC tambien.
        String[] igualdad = { "==", "!=" };
        for (int k = 0; k < igualdad.length; k++) {
            for (int i = 0; i < NUMEROS.length; i++) {
                for (int j = 0; j < NUMEROS.length; j++) {
                    poner(BINARIOS, igualdad[k], NUMEROS[i], NUMEROS[j], Tipo.BOOL);
                }
            }
            poner(BINARIOS, igualdad[k], Tipo.BOOL, Tipo.BOOL, Tipo.BOOL);
            poner(BINARIOS, igualdad[k], Tipo.LETRA, Tipo.LETRA, Tipo.BOOL);
            poner(BINARIOS, igualdad[k], Tipo.TXT, Tipo.TXT, Tipo.BOOL);
        }

        // ---- Logicos --------------------------------------------------------
        // Solo BOOL. No hay verdad implicita: una condicion numerica es error, y
        // es un error util, porque casi siempre lo que se quiso escribir fue una
        // comparacion.
        poner(BINARIOS, "&&", Tipo.BOOL, Tipo.BOOL, Tipo.BOOL);
        poner(BINARIOS, "||", Tipo.BOOL, Tipo.BOOL, Tipo.BOOL);

        // ---- Unarios --------------------------------------------------------
        UNARIOS.put(clave("-u", Tipo.ENT), Tipo.ENT);
        UNARIOS.put(clave("-u", Tipo.DEC), Tipo.DEC);
        UNARIOS.put(clave("!!", Tipo.BOOL), Tipo.BOOL);
    }

    private static void poner(Map<String, Tipo> m, String op, Tipo a, Tipo b, Tipo r) {
        m.put(op + "|" + a.lexema + "|" + b.lexema, r);
    }

    private static String clave(String op, Tipo a) {
        return op + "|" + a.lexema;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /**
     * Tipo que resulta de aplicar el operador a los dos tipos, o Tipo.ERROR si
     * esa combinacion no existe.
     *
     * Si alguno de los operandos ya venia con ERROR, el resultado es ERROR sin
     * mas: el fallo de verdad ya se reporto mas abajo y repetirlo aqui solo
     * produciria ruido.
     */
    public static Tipo resultado(Tipo izq, String op, Tipo der) {
        if (izq == null || der == null || izq.esError() || der.esError()) {
            return Tipo.ERROR;
        }
        Tipo r = BINARIOS.get(op + "|" + izq.lexema + "|" + der.lexema);
        return (r == null) ? Tipo.ERROR : r;
    }

    /** Tipo que resulta de un operador unario: menos unario o negacion logica. */
    public static Tipo resultadoUnario(String op, Tipo a) {
        if (a == null || a.esError()) {
            return Tipo.ERROR;
        }
        Tipo r = UNARIOS.get(clave(op, a));
        return (r == null) ? Tipo.ERROR : r;
    }

    /** true si esa suma es en realidad concatenacion de texto. */
    public static boolean esConcatenacion(String op, Tipo izq, Tipo der) {
        return "+".equals(op) && (izq == Tipo.TXT || der == Tipo.TXT);
    }

    /**
     * Compatibilidad de una asignacion.
     *
     * Devuelve ASIGNACION_OK, ASIGNACION_CONVERSION (el origen se amplia de ENT
     * a DEC) o ASIGNACION_ERROR.
     */
    public static int asignable(Tipo destino, Tipo origen) {
        if (destino == null || origen == null || destino.esError() || origen.esError()) {
            return ASIGNACION_OK;   // ya hubo un error antes: no se insiste
        }
        if (destino == origen) {
            return ASIGNACION_OK;
        }
        if (destino == Tipo.DEC && origen == Tipo.ENT) {
            return ASIGNACION_CONVERSION;
        }
        return ASIGNACION_ERROR;
    }

    /** true si el tipo sirve como variable de control de un SEGUN. */
    public static boolean admitidoEnSegun(Tipo t) {
        return t == Tipo.ENT || t == Tipo.LETRA || t == Tipo.TXT;
    }
}
