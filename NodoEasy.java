// Archivo: NodoEasy.java
//
// Superclase de TODOS los nodos del arbol sintactico.
//
// Se declara en EasyCompiler.jjt con la opcion NODE_CLASS = "NodoEasy", asi que
// JJTree hace que ASTPrograma, ASTAsignacion, ASTExpresionNivel1, ... la extiendan.
// Es el unico lugar donde se pueden agregar campos propios sin editar un archivo
// generado: las clases ASTxxx las regenera JJTree y estan en .gitignore.
//
// Que guarda y por que
// --------------------
// JJTree construye un nodo por produccion, pero NO convierte los operadores en
// nodos: el '+' de una suma se consume como token suelto dentro del nodo padre y
// desaparece de la estructura de hijos. Sin el operador no hay arbol de
// expresiones de verdad (tema 1.1) ni forma de comprobar tipos (tema 1.3).
//
// Por eso la gramatica "decora" cada nodo con la informacion LEXICA que vio al
// construirlo: el token del identificador, los operadores, si habia un menos
// unario, cuantos indices traia el acceso a un arreglo. Esto NO es analisis
// semantico -- no decide nada, solo registra lo que el parser ya tenia delante.
// Quien decide es AnalizadorSemantico, en una pasada posterior sobre el arbol.
import java.util.ArrayList;
import java.util.List;

public class NodoEasy extends SimpleNode {

    // ---- Decoracion puesta por la gramatica (informacion lexica) -------------

    /** Token del identificador o del literal que dio origen al nodo. */
    public Token tokenId;

    /** Lexema del tipo declarado ("ENT", "DEC", ...) en las declaraciones. */
    public String tipoDeclarado;

    /** Operadores binarios encontrados, en orden de aparicion. */
    public List<Token> operadores = new ArrayList<Token>();

    /** true si la expresion venia precedida por un menos unario. */
    public boolean unario;

    /** true si la condicion venia precedida por la negacion logica '!!'. */
    public boolean negado;

    /** true si la expresion venia entre parentesis: "(A+B)" y no "A[i]". */
    public boolean parentizada;

    /** Cantidad de indices del acceso: 0 escalar, 1 arreglo, 2 matriz. */
    public int numIndices;

    /** true si la declaracion traia " = <expresion>". */
    public boolean inicializada;

    /** Texto fuente de la expresion inicializadora, o null. */
    public String valorTexto;

    /** Dimensiones tal como se escribieron: "[9]" o "[3][4]". */
    public String dimensiones;

    // ---- Anotacion puesta por el analizador semantico ------------------------

    /** Tipo que el analizador semantico infirio para este nodo. */
    public Tipo tipoInferido;

    // ---- Constructores exigidos por JJTree ----------------------------------

    public NodoEasy(int id) {
        super(id);
    }

    public NodoEasy(EasyCompiler p, int id) {
        super(p, id);
    }

    // ---- Utilidades ---------------------------------------------------------

    /** Registra un operador binario visto por la gramatica. */
    public void agregarOperador(Token op) {
        if (op != null) {
            operadores.add(op);
        }
    }

    /** Devuelve el operador i-esimo, o null si no existe. */
    public Token operador(int i) {
        if (i < 0 || i >= operadores.size()) {
            return null;
        }
        return operadores.get(i);
    }

    /** Lexema del operador i-esimo ("+", "*", "&&"...), o null. */
    public String lexemaOperador(int i) {
        Token t = operador(i);
        return (t == null) ? null : t.image;
    }

    /** true si el nodo no tiene ningun operador registrado. */
    public boolean sinOperadores() {
        return operadores.isEmpty();
    }

    /** Hijo i-esimo con tolerancia a arboles incompletos: null si no existe. */
    public NodoEasy hijo(int i) {
        if (i < 0 || i >= jjtGetNumChildren()) {
            return null;
        }
        return (NodoEasy) jjtGetChild(i);
    }

    /** Linea donde empieza el nodo; -1 si JJTree no alcanzo a registrarla. */
    public int linea() {
        if (tokenId != null) return tokenId.beginLine;
        Token t = jjtGetFirstToken();
        return (t == null) ? -1 : t.beginLine;
    }

    /** Columna donde empieza el nodo; -1 si no se conoce. */
    public int columna() {
        if (tokenId != null) return tokenId.beginColumn;
        Token t = jjtGetFirstToken();
        return (t == null) ? -1 : t.beginColumn;
    }

    // ---- Atajos de decoracion, para que la gramatica quede en una linea -----
    //
    // La gramatica debe llamar, no razonar: el parser de Java que trae JJTree es
    // viejo (ni siquiera admite el operador diamante) y sus errores son
    // inservibles. Toda la logica vive aqui, donde la compila javac.

    /** Decora una declaracion de variable, constante, arreglo o matriz. */
    public void decorarDeclaracion(Token id, String tipo, boolean ini, String valor, String dim) {
        this.tokenId = id;
        this.tipoDeclarado = tipo;
        this.inicializada = ini;
        this.valorTexto = valor;
        this.dimensiones = dim;
    }

    /** Decora una asignacion: destino, operador (=, ++, --) y numero de indices. */
    public void decorarAsignacion(Token id, Token operador, int indices) {
        this.tokenId = id;
        this.numIndices = indices;
        agregarOperador(operador);
    }
}
