// Archivo: ImpresorArbol.java
//
// Recorre e imprime el arbol sintactico que JJTree construye durante el analisis.
//
// NO forma parte del codigo generado: SimpleNode.java, Node.java y
// EasyCompilerTreeConstants.java los produce JJTree en cada compilacion y no deben
// editarse a mano. Toda la logica de presentacion vive aqui.
//
// Idea central -----------------------------------------------------------------
// JJTree crea un nodo por cada produccion de la gramatica, pero NO crea nodos para
// los tokens. Con la opcion TRACK_TOKENS cada nodo guarda su rango de tokens
// [primerToken .. ultimoToken], y los rangos de sus hijos son contiguos y estan en
// orden. Por lo tanto, todo token del rango del padre que no cae dentro del rango de
// algun hijo es una hoja terminal de ese padre. Eso permite mostrar los tokens en el
// arbol sin anotar ni una sola produccion de la gramatica.

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImpresorArbol {

    // Dibujo de las ramas
    private static final String RAMA_MEDIA  = "├─ "; // |-
    private static final String RAMA_FINAL  = "└─ "; // `-
    private static final String LINEA_MEDIA = "│  "; // |
    private static final String LINEA_FINAL = "   ";

    // -----------------------------------------------------------------------
    // Niveles de paso de la cascada de precedencia (EasyCompiler.jjt).
    //
    // Un literal suelto como "5" atraviesa 9 producciones antes de llegar a la hoja.
    // En modo colapsado esos eslabones se omiten cuando no aportan nada: solo se
    // omite un nodo si es de esta lista, tiene exactamente un hijo y su rango de
    // tokens es identico al del hijo (o sea, no aporta ningun token propio).
    //
    // Es una lista explicita y no una regla generica de "todo nodo con un solo hijo"
    // para no colapsar por accidente cosas como Sentencias con una sola sentencia.
    // -----------------------------------------------------------------------
    private static final Set<String> NIVELES_DE_PASO = new HashSet<String>(Arrays.asList(
        "ExpresionAritmetica", "ExpresionNivel1", "ExpresionNivel2", "ExpresionNivel3",
        "ExpresionNivel4", "ValorConArreglos", "Valor", "Condicion", "CondicionSimple",
        "ExpresionConcatenada"
    ));

    // Mapa kind -> nombre simbolico del token (11 -> "ENTERO_DATO"), armado por
    // reflexion sobre EasyCompilerConstants para que se mantenga solo si la
    // gramatica cambia.
    private static final String[] NOMBRES_TOKEN = construirNombresDeToken();

    /** Devuelve el arbol completo como texto listo para imprimir. */
    public static String dibujar(SimpleNode raiz, boolean completo) {
        if (raiz == null) {
            return "  (no se construyo ningun arbol)\n";
        }
        StringBuilder sb = new StringBuilder();
        render(raiz, "", "", completo, sb);
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Dibuja un nodo y, recursivamente, todo lo que cuelga de el.
    //   prefijo  : barras verticales heredadas de los ancestros
    //   conector : rama intermedia o final ("" solo para la raiz)
    // -----------------------------------------------------------------------
    private static void render(SimpleNode nodo, String prefijo, String conector,
                               boolean completo, StringBuilder sb) {

        SimpleNode n = completo ? nodo : colapsar(nodo);

        sb.append(prefijo).append(conector).append(n.toString())
          .append(anotacion(n)).append('\n');

        String prefijoHijos = prefijo;
        if (conector.length() > 0) {
            prefijoHijos += conector.equals(RAMA_FINAL) ? LINEA_FINAL : LINEA_MEDIA;
        }

        List<Object> elementos = elementosDe(n);
        for (int i = 0; i < elementos.size(); i++) {
            String con = (i == elementos.size() - 1) ? RAMA_FINAL : RAMA_MEDIA;
            Object e = elementos.get(i);
            if (e instanceof SimpleNode) {
                render((SimpleNode) e, prefijoHijos, con, completo, sb);
            } else {
                sb.append(prefijoHijos).append(con)
                  .append(describirToken((Token) e)).append('\n');
            }
        }
    }

    // -----------------------------------------------------------------------
    // Anotacion que se dibuja a la derecha del nombre del nodo.
    //
    // JJTree crea un nodo por produccion pero NO convierte los operadores en
    // nodos: el '+' de una suma se consume como token suelto y desaparece de la
    // lista de hijos. Por eso la gramatica los anota en el propio nodo (ver
    // NodoEasy) y aqui se sacan a la luz: sin esto, un ExpresionNivel1 con una
    // suma y otro con una resta se dibujan exactamente igual.
    //
    // Es lo que convierte el arbol sintactico en un arbol de EXPRESIONES.
    // -----------------------------------------------------------------------
    private static String anotacion(SimpleNode nodo) {
        if (!(nodo instanceof NodoEasy)) return "";
        NodoEasy n = (NodoEasy) nodo;

        StringBuilder sb = new StringBuilder();

        // Operadores registrados: "[+]" o "[* /]" si el nivel encadeno varios.
        if (!n.sinOperadores()) {
            sb.append("  [");
            for (int i = 0; i < n.operadores.size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(n.operadores.get(i).image);
            }
            sb.append(']');
        }

        // Indices de un acceso a arreglo o matriz.
        if (n.numIndices == 1) sb.append("  [indexado]");
        if (n.numIndices == 2) sb.append("  [indexado x2]");

        // Tipo inferido, en cuanto el analizador semantico lo haya calculado.
        if (n.tipoInferido != null) sb.append("  {").append(n.tipoInferido).append('}');

        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Baja por la cadena de niveles de paso mientras el nodo no aporte nada
    // propio. Devuelve el primer nodo con contenido real.
    // -----------------------------------------------------------------------
    private static SimpleNode colapsar(SimpleNode nodo) {
        SimpleNode actual = nodo;
        while (NIVELES_DE_PASO.contains(actual.toString())
                && actual.jjtGetNumChildren() == 1) {

            SimpleNode hijo = (SimpleNode) actual.jjtGetChild(0);
            // Solo se omite si el hijo cubre exactamente el mismo rango de tokens:
            // asi no se pierden simbolos propios del padre, como el '-' unario de
            // ExpresionNivel4 o los parentesis de ValorConArreglos.
            if (hijo.jjtGetFirstToken() != actual.jjtGetFirstToken()
                    || hijo.jjtGetLastToken() != actual.jjtGetLastToken()) {
                break;
            }
            actual = hijo;
        }
        return actual;
    }

    // -----------------------------------------------------------------------
    // Intercala hijos y tokens sueltos en el orden en que aparecen en el codigo.
    // Devuelve una lista cuyos elementos son SimpleNode o Token.
    // -----------------------------------------------------------------------
    private static List<Object> elementosDe(SimpleNode nodo) {
        List<Object> elementos = new ArrayList<Object>();

        Token t = nodo.jjtGetFirstToken();
        Token ultimo = nodo.jjtGetLastToken();

        // Sin rango confiable no podemos intercalar; mostramos solo los hijos.
        if (t == null || ultimo == null) {
            for (int i = 0; i < nodo.jjtGetNumChildren(); i++) {
                elementos.add(nodo.jjtGetChild(i));
            }
            return elementos;
        }

        // Token en el que hay que detenerse. Si una produccion caso vacio, JJTree deja
        // ultimo.next == primero y el ciclo da cero vueltas por si solo.
        Token alto = ultimo.next;

        int i = 0;
        while (t != null && t != alto) {
            SimpleNode hijo = (i < nodo.jjtGetNumChildren())
                    ? (SimpleNode) nodo.jjtGetChild(i)
                    : null;

            if (hijo != null && hijo.jjtGetFirstToken() == t) {
                elementos.add(hijo);
                i++;
                Token finHijo = hijo.jjtGetLastToken();
                if (finHijo != null) {
                    // Si el hijo caso vacio, finHijo.next == t y no avanzamos: es
                    // correcto, porque el indice i si avanzo y el ciclo termina igual.
                    t = finHijo.next;
                }
            } else {
                elementos.add(t);
                t = t.next;
            }
        }

        // Cualquier hijo que haya quedado fuera del rango (no deberia ocurrir, pero
        // la recuperacion de errores puede dejar rangos raros) se agrega al final
        // para no perderlo del arbol.
        while (i < nodo.jjtGetNumChildren()) {
            elementos.add(nodo.jjtGetChild(i));
            i++;
        }

        return elementos;
    }

    /** Formatea una hoja terminal, por ejemplo: &lt;ENTERO_DATO: "ENT"&gt; */
    private static String describirToken(Token t) {
        String nombre = (t.kind >= 0 && t.kind < NOMBRES_TOKEN.length && NOMBRES_TOKEN[t.kind] != null)
                ? NOMBRES_TOKEN[t.kind]
                : ("token_" + t.kind);

        if (t.image == null || t.image.length() == 0) {
            return "<" + nombre + ">";
        }
        return "<" + nombre + ": \"" + escapar(t.image) + "\">";
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\"", "\\\"");
    }

    // -----------------------------------------------------------------------
    // Arma el mapa kind -> nombre leyendo las constantes enteras de
    // EasyCompilerConstants. Se descartan los nombres de los estados lexicos
    // (DEFAULT, IN_COMMENT) porque sus valores chocan con los de los primeros
    // tokens y no son tipos de token.
    // -----------------------------------------------------------------------
    private static String[] construirNombresDeToken() {
        String[] nombres = new String[EasyCompilerConstants.tokenImage.length];
        Set<String> estadosLexicos =
                new HashSet<String>(Arrays.asList(EasyCompilerTokenManager.lexStateNames));

        for (Field f : EasyCompilerConstants.class.getFields()) {
            if (f.getType() != int.class) continue;
            if (estadosLexicos.contains(f.getName())) continue;
            try {
                int valor = f.getInt(null);
                if (valor >= 0 && valor < nombres.length) {
                    nombres[valor] = f.getName();
                }
            } catch (IllegalAccessException e) {
                // Un campo inaccesible solo significa que ese token se mostrara por su
                // numero; no es motivo para interrumpir el analisis.
            }
        }
        return nombres;
    }
}
