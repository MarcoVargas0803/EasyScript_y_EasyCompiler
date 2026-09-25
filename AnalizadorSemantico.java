// Archivo: AnalizadorSemantico.java
//
// Analizador semantico de EasyScript. Es una PASADA APARTE sobre el arbol
// sintactico que ya construyo JJTree, no un conjunto de acciones dentro de la
// gramatica.
//
// Por que una pasada aparte
// -------------------------
// El analizador sintactico ya termino: su unico trabajo era decir si el
// programa esta bien FORMADO. Lo que se decide aqui es si ademas tiene SENTIDO:
// si los nombres existen, si los tipos encajan, si una constante no se
// reasigna. Separarlo deja el .jjt legible y convierte cada regla semantica en
// un metodo Java normal, con su error de compilacion y su prueba.
//
// Como se recorre el arbol
// ------------------------
// JJTree genero, con VISITOR = true, la interfaz EasyCompilerVisitor y un
// EasyCompilerDefaultVisitor que simplemente baja a los hijos. Heredamos de ese
// visitor por defecto, asi que todo nodo que no interese se recorre solo y aqui
// abajo solo aparecen los nodos donde de verdad hay algo que decidir.
//
// La pila de ambitos
// ------------------
// TablaSimbolos.abrirAmbito()/cerrarAmbito() se disparan al entrar y salir de
// visit(ASTSentencias). El visitor recorre el arbol en el mismo orden en que el
// parser lo construyo, asi que la pila de ambitos vuelve a estar VIVA justo
// cuando hay que resolver nombres: TablaSimbolos.buscar() funciona tal cual,
// sin reconstruir nada.
//
// La pila semantica
// -----------------
// Toda expresion deja EXACTAMENTE un Atributo en la pila. Un operador saca sus
// operandos, consulta el cubo de tipos y apila el resultado. Cada sentencia
// toma una marca al entrar y la restaura al salir, de modo que un subarbol roto
// (de los que deja la recuperacion de errores) no descuadre lo que viene
// despues.
//
// Una limitacion que conviene tener presente
// ------------------------------------------
// El recorrido es textual, de arriba abajo, y no sigue el flujo del programa.
// Por eso "declarar antes de usar" es una regla del lenguaje (como en C89), y
// por eso el aviso de "se usa sin tener valor" es una ADVERTENCIA y no un
// error: no se puede saber si el SI que le da valor llega a ejecutarse.
import java.util.ArrayList;
import java.util.List;

public class AnalizadorSemantico extends EasyCompilerDefaultVisitor {

    /** La pila semantica del tema 1.4. */
    private final PilaSemantica pila = new PilaSemantica();

    /**
     * El cuerpo de un PARA comparte ambito con su cabecera.
     *
     * visit(ASTBuclePara) abre el ambito antes de visitar la declaracion del
     * contador y deja esta bandera encendida para que el ASTSentencias del
     * cuerpo NO abra otro. Asi el contador vive dentro del ciclo (como en C o
     * Java) sin que aparezca un bloque de mas.
     */
    private boolean reutilizarAmbito = false;

    /**
     * Dentro de un LEER no tiene sentido avisar de "se usa sin tener valor":
     * precisamente lo que hace el LEER es darselo.
     */
    private boolean dentroDeLeer = false;

    /**
     * Punto de entrada. Recorre el arbol completo y deja los errores en
     * EasyCompiler.listaErrores y los simbolos en TablaSimbolos.
     *
     * @param raiz nodo Programa devuelto por el parser; si es null no hay nada
     *             que analizar (el parseo no llego ni a construir la raiz).
     */
    public void analizar(SimpleNode raiz) {
        if (raiz == null) {
            return;
        }
        raiz.jjtAccept(this, null);
    }

    // =====================================================================
    // AMBITOS
    // =====================================================================

    /**
     * Cada par de llaves es un ambito.
     *
     * El cierre va en un finally a proposito: si un subarbol incompleto (de los
     * que deja la recuperacion de errores sintacticos) hiciera fallar el
     * recorrido, un ambito sin cerrar desbalancearia la pila y todos los
     * nombres del resto del programa se resolverian mal.
     */
    public Object visit(ASTSentencias node, Object data) {
        if (reutilizarAmbito) {
            reutilizarAmbito = false;
            return node.childrenAccept(this, data);
        }
        TablaSimbolos.abrirAmbito();
        try {
            return node.childrenAccept(this, data);
        } finally {
            cerrarAmbitoAvisando();
        }
    }

    /**
     * Cierra el ambito actual, avisando antes de lo que se declaro y nunca se
     * uso.
     *
     * El aviso va justo aqui y no al final del programa porque este es el
     * momento en que se sabe: el bloque termino y el nombre ya no puede
     * aparecer.
     */
    private void cerrarAmbitoAvisando() {
        avisarNoUsados(TablaSimbolos.bloqueActual());
        TablaSimbolos.cerrarAmbito();
    }

    private void avisarNoUsados(int bloque) {
        List<Simbolo> simbolos = TablaSimbolos.simbolosDe(bloque);
        for (int i = 0; i < simbolos.size(); i++) {
            Simbolo s = simbolos.get(i);
            if (s.usado || "INDEFINIDO".equals(s.categoria)) {
                continue;
            }
            advertencia("ADV-01", s.linea, s.columna,
                "'" + s.nombre + "' se declaro pero nunca se uso.",
                "Eliminala si sobra, o revisa si escribiste mal el nombre en el punto donde querias usarla.");
        }
    }

    // =====================================================================
    // DECLARACIONES
    //
    // Es la accion que Aho deja supuesta en la Figura 5.8 (Compiladores, 2a
    // ed., pag. 315): "agregarTipo instala el tipo como el tipo del
    // identificador representado". Aqui se instala de verdad.
    //
    // El simbolo se instala DESPUES de evaluar el inicializador, igual que la
    // gramatica lo hacia. El orden importa: en "ENT X = X + 1;" la X de la
    // derecha todavia no debe existir.
    // =====================================================================

    public Object visit(ASTDeclaracionVariable node, Object data) {
        int marca = pila.marca();
        try {
            Atributo inicial = null;
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTCondicion) {
                    inicial = evaluarValor(h);
                } else {
                    h.jjtAccept(this, data);
                }
            }

            boolean esConstante = node.tokenId != null
                    && node.tokenId.kind == EasyCompilerConstants.CONSTANTE;

            if (esConstante && !node.inicializada) {
                error("SEM-02", node.tokenId,
                    "La constante '" + node.tokenId.image + "' se declaro sin valor.",
                    "Una constante recibe su valor una sola vez, en su declaracion. Por ejemplo: DEC CONST_Pi = 3.1416;");
            }

            avisarSiOculta(node.tokenId);
            Simbolo s = instalar(node, esConstante ? "CONSTANTE" : "VARIABLE");
            if (s != null) {
                TablaSimbolos.asignarDireccion(s);
                s.tieneValor = s.inicializada;
                if (inicial != null) {
                    comprobarInicializacion(s, inicial, node.tokenId);
                    // TRADUCCION: declarar con valor es, en codigo intermedio,
                    // una asignacion corriente. La declaracion en si no genera
                    // ninguna instruccion: solo reserva espacio, y de eso ya se
                    // encargo la tabla de direcciones.
                    if (!inicial.esError()) {
                        GeneradorCodigo.emitirAsignacion(s.nombre, inicial, Tipo.desdeLexema(s.tipo));
                    }
                }
            }
            return data;
        } finally {
            pila.restaurar(marca);
        }
    }

    public Object visit(ASTDeclaracionArreglo node, Object data) {
        int marca = pila.marca();
        try {
            Atributo tamano = null;
            List<Atributo> valores = new ArrayList<Atributo>();
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTExpresionAritmetica && tamano == null) {
                    tamano = evaluar(h);
                } else if (h instanceof ASTCondicion) {
                    valores.add(evaluarValor(h));
                } else {
                    h.jjtAccept(this, data);
                }
            }

            avisarSiOculta(node.tokenId);
            Simbolo s = instalar(node, "ARREGLO");
            if (s == null) {
                return data;
            }
            s.tieneValor = s.inicializada;
            s.filas = tamanoDeclarado(tamano, node.tokenId, "arreglo");
            // La direccion se asigna ahora y no en instalar(): hasta aqui no se
            // sabia cuantos elementos reservar.
            TablaSimbolos.asignarDireccion(s);
            comprobarValoresIniciales(s, valores, s.filas, node.tokenId);
            emitirValoresIniciales(s, valores);
            return data;
        } finally {
            pila.restaurar(marca);
        }
    }

    public Object visit(ASTDeclaracionMatriz node, Object data) {
        int marca = pila.marca();
        try {
            List<Atributo> dimensiones = new ArrayList<Atributo>();
            List<Atributo> valores = new ArrayList<Atributo>();
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTExpresionAritmetica && dimensiones.size() < 2) {
                    dimensiones.add(evaluar(h));
                } else if (h instanceof ASTCondicion) {
                    valores.add(evaluarValor(h));
                } else {
                    h.jjtAccept(this, data);
                }
            }

            avisarSiOculta(node.tokenId);
            Simbolo s = instalar(node, "MATRIZ");
            if (s == null) {
                return data;
            }
            s.tieneValor = s.inicializada;
            s.filas = tamanoDeclarado(dimension(dimensiones, 0), node.tokenId, "matriz");
            s.columnas = tamanoDeclarado(dimension(dimensiones, 1), node.tokenId, "matriz");
            TablaSimbolos.asignarDireccion(s);

            // El arbol aplana el inicializador: { {1,2}, {3,4} } llega como
            // cuatro valores sueltos, sin la estructura de filas. Por eso se
            // comprueba el TOTAL de elementos y no la forma de cada fila.
            int total = (s.filas > 0 && s.columnas > 0) ? s.filas * s.columnas : 0;
            comprobarValoresIniciales(s, valores, total, node.tokenId);
            emitirValoresIniciales(s, valores);
            return data;
        } finally {
            pila.restaurar(marca);
        }
    }

    private Atributo dimension(List<Atributo> dims, int i) {
        return (i < dims.size()) ? dims.get(i) : null;
    }

    /**
     * Instala en la tabla lo que la gramatica dejo anotado en el nodo.
     *
     * Un identificador que el escaner ya rechazo no se instala: su error lexico
     * ya se reporto y meterlo en la tabla solo produciria un segundo error,
     * derivado del primero.
     */
    private Simbolo instalar(NodoEasy node, String categoria) {
        Token id = node.tokenId;
        if (id == null || id.kind == EasyCompilerConstants.ERROR_IDENTIFICADOR_INVALIDO) {
            return null;
        }
        String dim = (node.dimensiones == null) ? "-" : node.dimensiones;
        String valor = (node.valorTexto == null) ? "-" : node.valorTexto;
        return TablaSimbolos.agregarTipo(id, node.tipoDeclarado, categoria, dim,
                                         node.inicializada, valor);
    }

    /** Avisa cuando una declaracion tapa otra de un bloque exterior. */
    private void avisarSiOculta(Token id) {
        if (id == null || id.kind == EasyCompilerConstants.ERROR_IDENTIFICADOR_INVALIDO) {
            return;
        }
        Simbolo previo = TablaSimbolos.buscar(id.image);
        if (previo == null || previo.bloque == TablaSimbolos.bloqueActual()) {
            return;   // el duplicado en el mismo bloque es SEM-01, no esto
        }
        advertencia("ADV-03", id.beginLine, id.beginColumn,
            "'" + id.image + "' oculta otra declaracion del mismo nombre (linea "
                + previo.linea + ", columna " + previo.columna + ").",
            "Dentro de este bloque solo se ve la nueva. Renombra una de las dos si no era lo que querias.");
    }

    /** Comprueba que el valor inicial encaje con el tipo declarado. */
    private void comprobarInicializacion(Simbolo s, Atributo inicial, Token id) {
        Tipo destino = Tipo.desdeLexema(s.tipo);
        if (CuboSemantico.asignable(destino, inicial.tipo) == CuboSemantico.ASIGNACION_ERROR) {
            if (destino == Tipo.ENT && inicial.tipo == Tipo.DEC) {
                error("SEM-04", id,
                    "No se puede inicializar '" + s.nombre + "' (ENT) con un valor DEC: se perderian los decimales.",
                    "Declara '" + s.nombre + "' como DEC, o usa un valor entero.");
            } else {
                error("SEM-03", id,
                    "No se puede inicializar '" + s.nombre + "', de tipo " + destino
                        + ", con un valor de tipo " + inicial.tipo + ".",
                    "Usa un valor del mismo tipo, o cambia el tipo de la declaracion.");
            }
        }
    }

    /**
     * El tamano de un arreglo o matriz tiene que ser un entero conocido al
     * compilar: es lo que permite reservar memoria y comprobar los indices.
     * Devuelve 0 si no se pudo determinar.
     */
    private int tamanoDeclarado(Atributo dim, Token id, String que) {
        if (dim == null || dim.esError() || id == null) {
            return 0;
        }
        String texto = dim.esConstanteConocida() ? dim.valorConstante.trim() : null;
        int n = -1;
        if (texto != null && dim.tipo == Tipo.ENT) {
            try {
                n = Integer.parseInt(texto);
            } catch (NumberFormatException e) {
                n = -1;
            }
        }
        if (n <= 0) {
            error("SEM-05", id,
                "El tamano de " + que + " '" + id.image + "' debe ser un numero entero mayor que cero; se recibio '"
                    + dim.lexema + "'.",
                "Escribe el tamano como un literal entero. Por ejemplo: ARREGLO ENT Notas[10];");
            return 0;
        }
        return n;
    }

    /**
     * Emite la carga inicial de un ARREGLO o una MATRIZ.
     *
     * La lista entre llaves no es una instruccion: se traduce como una escritura
     * indexada por cada valor. El desplazamiento se puede calcular aqui mismo
     * porque la posicion es conocida al compilar, asi que no hace falta gastar
     * un temporal en multiplicarla.
     *
     * Una matriz llega aplanada por el arbol, de modo que las posiciones se
     * cuentan por filas, que es como estan guardadas.
     */
    private void emitirValoresIniciales(Simbolo s, List<Atributo> valores) {
        for (int i = 0; i < valores.size(); i++) {
            Atributo v = valores.get(i);
            if (v.esError()) {
                continue;
            }
            GeneradorCodigo.emitir("[]=", s.nombre,
                String.valueOf(i * s.anchoElemento), v.lugar);
        }
    }

    /** Cantidad y tipo de los valores entre llaves de un ARREGLO o MATRIZ. */
    private void comprobarValoresIniciales(Simbolo s, List<Atributo> valores, int esperados, Token id) {
        if (valores.isEmpty()) {
            return;
        }
        if (esperados > 0 && valores.size() != esperados) {
            error("SEM-06", id,
                "'" + s.nombre + "' declara " + esperados + " elemento(s) pero recibe "
                    + valores.size() + " valor(es).",
                "Iguala la cantidad de valores al tamano declarado, o ajusta el tamano.");
        }
        Tipo base = Tipo.desdeLexema(s.tipo);
        for (int i = 0; i < valores.size(); i++) {
            Atributo v = valores.get(i);
            if (v.esError()) {
                continue;
            }
            if (CuboSemantico.asignable(base, v.tipo) == CuboSemantico.ASIGNACION_ERROR) {
                error("SEM-08", v.token,
                    "El valor '" + v.lexema + "' (posicion " + (i + 1) + ") no es de tipo "
                        + base + ", que es el tipo base de '" + s.nombre + "'.",
                    "Todos los valores de la lista deben ser del tipo base declarado.");
            }
        }
    }

    // =====================================================================
    // EXPRESIONES: aqui trabaja la pila semantica
    //
    // Contrato que cumplen TODAS las visitas de expresion: al terminar dejan
    // exactamente un Atributo en la pila. Es lo que permite encadenarlas sin
    // pasarse valores.
    // =====================================================================

    /** Hoja: un literal o una referencia a un identificador. */
    public Object visit(ASTValor node, Object data) {
        Token t = node.tokenId;
        if (t == null) {
            pila.apilar(Atributo.error(null));
            return data;
        }
        if (t.kind == EasyCompilerConstants.VARIABLE || t.kind == EasyCompilerConstants.CONSTANTE) {
            pila.apilar(resolver(t));
        } else {
            comprobarRangoDelLiteral(t);
            pila.apilar(Atributo.deLiteral(t));
        }
        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    /**
     * Un valor, posiblemente con indices, o una expresion entre parentesis.
     *
     * La comprobacion de "arreglo usado sin indice" tiene que vivir AQUI y no en
     * ASTValor: es este nodo el unico que sabe si vinieron corchetes. Puesta un
     * nivel mas abajo, cada A[0] legitimo daria un error falso.
     */
    public Object visit(ASTValorConArreglos node, Object data) {
        if (node.parentizada) {
            evaluarYApilar(node.hijo(0));
            // Estamos dentro de una expresion, o sea en contexto de VALOR. Si lo
            // que habia entre parentesis era una condicion con && o ||, llega en
            // forma de saltos y hay que convertirla en un booleano de verdad.
            pila.apilar(materializar(pila.desapilar()));
            node.tipoInferido = pila.cima().tipo;
            return data;
        }

        Atributo base = evaluar(node.hijo(0));

        // Los indices se evaluan siempre, aunque la base este mal: asi los
        // errores que haya dentro de ellos tambien se reportan.
        Atributo indiceFila = null;
        Atributo indiceColumna = null;
        for (int i = 1; i <= node.numIndices; i++) {
            Atributo indice = evaluar(node.hijo(i));
            comprobarIndice(indice);
            comprobarRango(indice, base.simbolo, i);
            if (i == 1) {
                indiceFila = indice;
            } else {
                indiceColumna = indice;
            }
        }

        Atributo resultado = accesoIndexado(base, node);

        // TRADUCCION: leer un elemento son dos pasos. Primero se calcula en que
        // byte empieza (el indice por el ancho del elemento) y luego se lee de
        // ahi. Separarlo es lo que permite que el indice sea una expresion
        // cualquiera y no solo un numero escrito a mano.
        if (!resultado.esError() && base.simbolo != null
                && node.numIndices > 0 && indiceFila != null) {
            String desplazamiento =
                GeneradorCodigo.emitirDesplazamiento(base.simbolo, indiceFila, indiceColumna);
            String t = GeneradorCodigo.nuevoTemporal(resultado.tipo);
            GeneradorCodigo.emitir("=[]", base.simbolo.nombre, desplazamiento, t);
            resultado = Atributo.temporal(resultado.tipo, base.lexema + "[...]", t, base.token);
        }

        pila.apilar(resultado);
        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    public Object visit(ASTExpresionAritmetica node, Object data) {
        evaluarYApilar(node.hijo(0));
        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    public Object visit(ASTExpresionNivel1 node, Object data) {
        return reducirCadena(node, data);
    }

    public Object visit(ASTExpresionNivel2 node, Object data) {
        return reducirCadena(node, data);
    }

    /**
     * Menos unario. Esta ENCIMA del exponente, no debajo: en "-2 ** 2" el signo
     * se aplica al resultado de la potencia, no a la base.
     */
    public Object visit(ASTExpresionNivel3 node, Object data) {
        evaluarYApilar(node.hijo(0));
        if (node.unario) {
            Atributo a = pila.desapilar();
            Tipo r = CuboSemantico.resultadoUnario("-u", a.tipo);
            if (r.esError() && !a.esError()) {
                error("SEM-27", node.operador(0),
                    "El signo negativo solo se aplica a ENT o DEC; se recibio " + a.tipo + ".",
                    "Quita el signo, o usa un valor numerico.");
            }
            String lugar = GeneradorCodigo.emitirUnaria("-u", a, r);
            pila.apilar(Atributo.temporal(r, "-" + a.lexema, lugar, node.operador(0)));
        }
        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    public Object visit(ASTExpresionConcatenada node, Object data) {
        return reducirCadena(node, data);
    }

    /**
     * Exponente. Asocia a la derecha, asi que este nodo tiene como mucho un
     * operador y su hijo derecho ya trae reducido todo lo que viene despues.
     */
    public Object visit(ASTExpresionNivel4 node, Object data) {
        return reducirCadena(node, data);
    }

    /**
     * Una condicion simple: una expresion, opcionalmente comparada con otra y
     * opcionalmente negada.
     */
    public Object visit(ASTCondicionSimple node, Object data) {
        evaluarYApilar(node.hijo(0));

        // Con tres hijos, el de en medio es el operador relacional.
        Token opRel = operadorRelacionalDe(node);
        if (opRel != null) {
            evaluarYApilar(node.hijo(2));
            Atributo der = pila.desapilar();
            Atributo izq = pila.desapilar();
            Tipo r = CuboSemantico.resultado(izq.tipo, opRel.image, der.tipo);
            if (r.esError() && !izq.esError() && !der.esError()) {
                reportarRelacionalInvalida(opRel, izq, der);
            }
            String lugar = GeneradorCodigo.emitirBinaria(opRel.image, izq, der, r);
            pila.apilar(Atributo.temporal(r, izq.lexema + " " + opRel.image + " " + der.lexema, lugar, opRel));
        }

        if (node.negado) {
            Atributo a = pila.desapilar();
            Tipo r = CuboSemantico.resultadoUnario("!!", a.tipo);
            if (r.esError() && !a.esError()) {
                error("SEM-26", a.token,
                    "La negacion '!!' solo se aplica a valores BOOL; se recibio " + a.tipo + ".",
                    "Niega una condicion completa. Por ejemplo: !!(Numero > 5).");
            }
            String lugar = GeneradorCodigo.emitirUnaria("!!", a, r);
            pila.apilar(Atributo.temporal(r, "!!" + a.lexema, lugar, a.token));
        }

        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    /**
     * Condiciones unidas por && o ||. Aqui vive el cortocircuito.
     *
     * La idea es no calcular de mas. En "A && B", si A ya salio falsa, B no
     * cambia nada: sobra evaluarla. Eso no es solo una optimizacion, es lo que
     * hace seguro escribir algo como (I < N && Datos[I] > 0), donde evaluar la
     * segunda parte con I fuera de rango seria un problema.
     *
     * Para conseguirlo, el operando izquierdo se convierte en SALTOS antes de
     * mirar siquiera el derecho, y el codigo del derecho se emite despues de
     * ese salto. Asi el salto lo puede esquivar entero.
     *
     * El detalle esta en que, al emitir el salto, todavia no se sabe a donde
     * va: el destino depende de si la condicion acaba dentro de un SI, de un
     * MIENTRAS o asignada a una variable, y eso se descubre mas arriba en el
     * arbol. La solucion es el BACKPATCHING (Aho, Compiladores 2a ed., seccion
     * 6.7): el salto se emite con el destino en blanco, su numero de
     * instruccion se apunta en una lista, y la lista se rellena cuando el
     * destino se conoce.
     *
     * Las reglas de combinacion son las del libro:
     *
     *   B -> B1 && B2   la lista VERDADERA de B1 apunta al inicio de B2
     *                   B.verdadero = B2.verdadero
     *                   B.falso     = B1.falso + B2.falso
     *
     *   B -> B1 || B2   la lista FALSA de B1 apunta al inicio de B2
     *                   B.verdadero = B1.verdadero + B2.verdadero
     *                   B.falso     = B2.falso
     *
     * Dicho en voz alta: con &&, si el izquierdo sale cierto todavia no sabemos
     * nada y hay que seguir mirando; si sale falso, ya terminamos. Con ||, al
     * reves.
     */
    public Object visit(ASTCondicion node, Object data) {
        evaluarYApilar(node.hijo(0));

        for (int i = 0; i < node.operadores.size(); i++) {
            Token op = node.operador(i);
            boolean esY = "&&".equals(op.image);
            Atributo izq = pila.desapilar();

            // Si el izquierdo no es booleano hay un error de tipos, y montar el
            // cortocircuito sobre el no tendria sentido: se evalua todo de
            // forma corriente y el error se reporta igual, unas lineas mas
            // abajo.
            boolean cortocircuito = (izq.tipo == Tipo.BOOL);

            Atributo saltosIzq = null;
            if (cortocircuito) {
                saltosIzq = aSaltos(izq);
                // El marcador M de Aho: este es el punto donde empieza el
                // operando derecho, y por tanto el destino del salto que dice
                // "con lo que se del izquierdo todavia no basta".
                String entradaDerecha = GeneradorCodigo.etiquetaAqui();
                GeneradorCodigo.completar(
                    esY ? saltosIzq.listaVerdadero : saltosIzq.listaFalso, entradaDerecha);
            }

            evaluarYApilar(node.hijo(i + 1));
            Atributo der = pila.desapilar();

            Tipo r = CuboSemantico.resultado(izq.tipo, op.image, der.tipo);
            if (r.esError() && !izq.esError() && !der.esError()) {
                Atributo culpable = (izq.tipo == Tipo.BOOL) ? der : izq;
                String lado = (izq.tipo == Tipo.BOOL) ? "derecho" : "izquierdo";
                error("SEM-25", culpable.token,
                    "El operador '" + op.image + "' necesita condiciones BOOL; el operando "
                        + lado + " es " + culpable.tipo + ".",
                    "Compara primero. Por ejemplo: SI (Edad >= 18 && Tiene_id == VERDADERO).");
            }

            String lexema = izq.lexema + " " + op.image + " " + der.lexema;

            if (cortocircuito) {
                Atributo saltosDer = aSaltos(der);
                Atributo res = Atributo.saltos(r, lexema, op);
                if (esY) {
                    res.listaVerdadero = saltosDer.listaVerdadero;
                    res.listaFalso =
                        GeneradorCodigo.unir(saltosIzq.listaFalso, saltosDer.listaFalso);
                } else {
                    res.listaVerdadero =
                        GeneradorCodigo.unir(saltosIzq.listaVerdadero, saltosDer.listaVerdadero);
                    res.listaFalso = saltosDer.listaFalso;
                }
                pila.apilar(res);
            } else {
                String lugar = GeneradorCodigo.emitirBinaria(op.image, izq, der, r);
                pila.apilar(Atributo.temporal(r, lexema, lugar, op));
            }
        }
        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    // =====================================================================
    // LAS DOS CARAS DE UNA CONDICION
    //
    // Una condicion puede hacer falta de dos formas, y hay que saber pasar de
    // una a otra:
    //
    //   - como SALTOS, cuando dirige un SI o un MIENTRAS. No se calcula nada:
    //     el codigo salta a un sitio o a otro.
    //   - como VALOR, cuando se guarda en una variable o se imprime. Entonces
    //     si hay que producir un booleano de verdad.
    //
    // Aho lo trata en la seccion 6.6.6: no existe "la" traduccion de una
    // condicion, sino la que pide el contexto donde aparece.
    // =====================================================================

    /**
     * Convierte un valor booleano en saltos.
     *
     * Si ya venia como saltos se devuelve tal cual. Si venia como valor, se
     * emiten sus dos salidas -una si es cierto y otra si no-, ambas con el
     * destino todavia en blanco.
     */
    private Atributo aSaltos(Atributo v) {
        if (v.esSaltos()) {
            return v;
        }
        Atributo c = Atributo.saltos(v.tipo, v.lexema, v.token);
        c.listaVerdadero = GeneradorCodigo.nuevaLista(
            GeneradorCodigo.emitir("si_verdadero", v.lugar, "-", GeneradorCodigo.PENDIENTE));
        c.listaFalso = GeneradorCodigo.nuevaLista(
            GeneradorCodigo.emitir("ir_a", "-", "-", GeneradorCodigo.PENDIENTE));
        return c;
    }

    /**
     * Convierte saltos en un valor booleano guardado en un temporal.
     *
     * Cada camino llega a una asignacion distinta:
     *
     *   (por el camino verdadero):  t = VERDADERO ; ir_a fin
     *   (por el camino falso):      t = FALSO
     *   fin:
     *
     * Hace falta siempre que la condicion se use como dato y no como control:
     * una declaracion como BOOL Mayor = Edad > 18 && Tiene_id; tiene que dejar
     * algo dentro de Mayor.
     */
    private Atributo materializar(Atributo c) {
        if (c == null || !c.esSaltos()) {
            return c;
        }
        String t = GeneradorCodigo.nuevoTemporal(Tipo.BOOL);
        String fin = GeneradorCodigo.nuevaEtiqueta();

        GeneradorCodigo.completar(c.listaVerdadero, GeneradorCodigo.etiquetaAqui());
        GeneradorCodigo.emitir("=", "VERDADERO", "-", t);
        GeneradorCodigo.emitir("ir_a", "-", "-", fin);

        GeneradorCodigo.completar(c.listaFalso, GeneradorCodigo.etiquetaAqui());
        GeneradorCodigo.emitir("=", "FALSO", "-", t);
        GeneradorCodigo.etiquetar(fin);

        return Atributo.temporal(c.tipo, c.lexema, t, c.token);
    }

    /**
     * Emite la salida de una estructura de control cuando su condicion es falsa.
     *
     * Sirve para las dos formas: si la condicion trae saltos pendientes se
     * rellenan (el camino falso sale, el verdadero entra al cuerpo); si trae un
     * valor, basta un si_falso corriente.
     */
    private void saltarSiFalso(Atributo c, String etiquetaFalso) {
        if (c.esSaltos()) {
            GeneradorCodigo.completar(c.listaFalso, etiquetaFalso);
            GeneradorCodigo.completar(c.listaVerdadero, GeneradorCodigo.etiquetaAqui());
            return;
        }
        GeneradorCodigo.emitir("si_falso", c.lugar, "-", etiquetaFalso);
    }

    /** Evalua un hijo en un contexto de VALOR: si trae saltos, los materializa. */
    private Atributo evaluarValor(NodoEasy hijo) {
        return materializar(evaluar(hijo));
    }

    /**
     * Reduce un nivel de expresion con N operandos y N-1 operadores.
     *
     * Es la pila semantica en su forma mas pura: se apila el primer operando y,
     * por cada operador, se apila el siguiente, se sacan los dos y se apila el
     * resultado.
     */
    private Object reducirCadena(NodoEasy node, Object data) {
        evaluarYApilar(node.hijo(0));
        for (int i = 0; i < node.operadores.size(); i++) {
            evaluarYApilar(node.hijo(i + 1));
            reducirBinaria(node.operador(i));
        }
        node.tipoInferido = pila.cima().tipo;
        return data;
    }

    /** Saca dos operandos, consulta el cubo, reporta si procede y apila. */
    private void reducirBinaria(Token op) {
        Atributo der = pila.desapilar();
        Atributo izq = pila.desapilar();
        String lexemaOp = op.image;

        Tipo r = CuboSemantico.resultado(izq.tipo, lexemaOp, der.tipo);

        if (r.esError() && !izq.esError() && !der.esError()) {
            if ("%".equals(lexemaOp)) {
                error("SEM-21", op,
                    "El operador '%' solo opera entre valores ENT; se recibio "
                        + izq.tipo + " y " + der.tipo + ".",
                    "El residuo esta definido sobre enteros. Usa expresiones ENT.");
            } else {
                error("SEM-20", op,
                    "El operador '" + lexemaOp + "' no puede aplicarse entre "
                        + izq.tipo + " y " + der.tipo + ".",
                    "Las operaciones aritmeticas combinan ENT y DEC. La suma ademas concatena cuando hay TXT.");
            }
        }

        comprobarDivisionEntreCero(op, der);

        // TRADUCCION: la operacion se convierte en un cuadruplo y su resultado
        // deja de ser una idea para pasar a vivir en un temporal concreto.
        String lugar = GeneradorCodigo.emitirBinaria(lexemaOp, izq, der, r);
        pila.apilar(Atributo.temporal(r, izq.lexema + " " + lexemaOp + " " + der.lexema, lugar, op));
    }

    // =====================================================================
    // SENTENCIAS
    // =====================================================================

    public Object visit(ASTAsignacion node, Object data) {
        int marca = pila.marca();
        try {
            Token id = node.tokenId;
            if (id == null || id.kind == EasyCompilerConstants.ERROR_IDENTIFICADOR_INVALIDO) {
                return data;   // el error lexico ya se reporto
            }

            // Se busca el simbolo antes de evaluar nada para poder comprobar el
            // rango de los indices contra su tamano declarado. Aqui no se
            // reporta si no existe: de eso se encarga resolverDestino.
            Simbolo destinoSimbolo = TablaSimbolos.buscar(id.image);

            // Los hijos se eligen por su CLASE, no por su posicion.
            //
            // Un ASTAsignacion tiene ademas un ASTPuntoYComaVirtual al final, y
            // en su dia este bucle lo tomaba por la expresion asignada: el valor
            // de verdad quedaba pisado por un operando vacio y la comprobacion
            // de tipos se saltaba en silencio.
            Atributo origen = null;
            Atributo indiceFila = null;
            Atributo indiceColumna = null;
            int indicesVistos = 0;
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTExpresionAritmetica && indicesVistos < node.numIndices) {
                    indicesVistos++;
                    Atributo indice = evaluar(h);
                    comprobarIndice(indice);
                    comprobarRango(indice, destinoSimbolo, indicesVistos);
                    if (indicesVistos == 1) {
                        indiceFila = indice;
                    } else {
                        indiceColumna = indice;
                    }
                } else if (h instanceof ASTCondicion) {
                    origen = evaluarValor(h);
                } else {
                    h.jjtAccept(this, data);
                }
            }

            Atributo destino = resolverDestino(node, id);
            if (destino.esError()) {
                return data;
            }

            Token op = node.operador(0);
            if (op != null && op.kind != EasyCompilerConstants.IGUAL_ASIGNACION) {
                comprobarIncremento(destino, op);
                // TRADUCCION: X++ no es un operador del codigo intermedio; es
                // una suma corriente escrita de forma corta.
                String aritmetico = (op.kind == EasyCompilerConstants.INCREMENTO_AL_VALOR) ? "+" : "-";
                GeneradorCodigo.emitir(aritmetico, id.image, "1", id.image);
            } else if (origen != null) {
                comprobarAsignacion(destino, origen, id);
                emitirAsignacionTraducida(destino, origen, indiceFila, indiceColumna);
            }

            // A partir de aqui la variable ya tiene contenido.
            if (destino.simbolo != null) {
                destino.simbolo.tieneValor = true;
            }
            return data;
        } finally {
            pila.restaurar(marca);
        }
    }

    /**
     * Emite la asignacion ya comprobada.
     *
     * Escribir en una variable es una copia. Escribir en un elemento de un
     * arreglo son dos instrucciones: calcular en que byte cae y guardar ahi.
     */
    private void emitirAsignacionTraducida(Atributo destino, Atributo origen,
                                           Atributo indiceFila, Atributo indiceColumna) {
        if (origen.esError() || destino.simbolo == null) {
            return;
        }
        Simbolo s = destino.simbolo;

        if (indiceFila == null) {
            GeneradorCodigo.emitirAsignacion(s.nombre, origen, destino.tipo);
            return;
        }

        String desplazamiento = GeneradorCodigo.emitirDesplazamiento(s, indiceFila, indiceColumna);
        GeneradorCodigo.emitir("[]=", s.nombre, desplazamiento, origen.lugar);
    }

    // =====================================================================
    // ESTRUCTURAS DE CONTROL Y SU TRADUCCION
    //
    // Aqui el recorrido deja de ser "visita a los hijos" y pasa a tener un
    // orden propio, porque el codigo generado no sale en el mismo orden en que
    // esta escrito el programa: la etiqueta de un MIENTRAS va ANTES de su
    // condicion, y el paso de un PARA se escribe en la cabecera pero se ejecuta
    // DESPUES del cuerpo.
    //
    // Es la parte del esquema de traduccion donde se ve que el codigo de tres
    // direcciones no tiene bloques ni anidamiento: solo saltos (Aho, seccion
    // 6.6). Todo SI, MIENTRAS o SEGUN se reduce a comparar y saltar.
    // =====================================================================

    /**
     * SI (c1) { S1 } CONTRARIO (c2) { S2 } SINO { S3 }
     *
     *          si_falso c1 ir_a Lf1
     *          S1
     *          ir_a Lfin
     *   Lf1:   si_falso c2 ir_a Lf2
     *          S2
     *          ir_a Lfin
     *   Lf2:   S3
     *   Lfin:
     *
     * Los hijos llegan como parejas (Condicion, Sentencias), y un ASTSentencias
     * suelto al final es el SINO. Cada CONTRARIO pide su propia etiqueta de
     * fallo, y todos los caminos confluyen en la misma Lfin.
     */
    public Object visit(ASTCondicionalSi node, Object data) {
        String etiquetaFin = GeneradorCodigo.nuevaEtiqueta();
        int i = 0;
        while (i < node.jjtGetNumChildren()) {
            NodoEasy h = node.hijo(i);
            if (h == null) {
                i++;
                continue;
            }
            if (h instanceof ASTCondicion) {
                Atributo c = condicionBooleana(h, "SI");
                String etiquetaFallo = GeneradorCodigo.nuevaEtiqueta();
                saltarSiFalso(c, etiquetaFallo);

                NodoEasy cuerpo = node.hijo(i + 1);
                if (cuerpo != null) {
                    cuerpo.jjtAccept(this, data);
                }
                GeneradorCodigo.emitir("ir_a", "-", "-", etiquetaFin);
                GeneradorCodigo.etiquetar(etiquetaFallo);
                i += 2;
            } else {
                // ASTSentencias suelto: es el bloque del SINO.
                h.jjtAccept(this, data);
                i++;
            }
        }
        GeneradorCodigo.etiquetar(etiquetaFin);
        return data;
    }

    /**
     * MIENTRAS (c) { S }
     *
     *   Lini:  si_falso c ir_a Lfin
     *          S
     *          ir_a Lini
     *   Lfin:
     *
     * La etiqueta de entrada va antes de la condicion porque hay que volver a
     * evaluarla en cada vuelta, no solo la primera.
     */
    public Object visit(ASTBucleMientras node, Object data) {
        String etiquetaInicio = GeneradorCodigo.nuevaEtiqueta();
        String etiquetaFin = GeneradorCodigo.nuevaEtiqueta();

        GeneradorCodigo.etiquetar(etiquetaInicio);

        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            NodoEasy h = node.hijo(i);
            if (h == null) {
                continue;
            }
            if (h instanceof ASTCondicion) {
                Atributo c = condicionBooleana(h, "MIENTRAS");
                saltarSiFalso(c, etiquetaFin);
            } else {
                h.jjtAccept(this, data);
            }
        }

        GeneradorCodigo.emitir("ir_a", "-", "-", etiquetaInicio);
        GeneradorCodigo.etiquetar(etiquetaFin);
        return data;
    }

    /**
     * PARA (ENT I = 0; I < 10; I++) { S }
     *
     *          I = 0
     *   Lini:  si_falso I < 10 ir_a Lfin
     *          S
     *          I = I + 1
     *          ir_a Lini
     *   Lfin:
     *
     * Dos cosas que no son evidentes:
     *
     * 1. El ambito se abre AQUI, antes que nada, para que el contador quede
     *    dentro del ciclo. Mientras esto se hacia en la gramatica no habia
     *    forma: la declaracion de la cabecera corria antes de que el cuerpo
     *    abriera su ambito, asi que el contador caia en el bloque de fuera y
     *    dos PARA hermanos con "ENT I" se denunciaban como repetidos.
     *
     * 2. El paso se escribe en la cabecera pero se EJECUTA al final de cada
     *    vuelta. Por eso la gramatica guarda su variable y su operador en el
     *    nodo (ver NodoEasy) y el cuadruplo se emite despues del cuerpo. Es el
     *    unico sitio donde el orden del codigo fuente y el del codigo generado
     *    no coinciden.
     */
    public Object visit(ASTBuclePara node, Object data) {
        TablaSimbolos.abrirAmbito();
        try {
            reutilizarAmbito = true;

            String etiquetaInicio = GeneradorCodigo.nuevaEtiqueta();
            String etiquetaFin = GeneradorCodigo.nuevaEtiqueta();
            boolean inicioEtiquetado = false;

            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTCondicion) {
                    // La etiqueta va justo antes de la condicion: la inicializacion
                    // corre una sola vez y queda fuera del ciclo.
                    GeneradorCodigo.etiquetar(etiquetaInicio);
                    inicioEtiquetado = true;
                    Atributo c = condicionBooleana(h, "PARA");
                    saltarSiFalso(c, etiquetaFin);
                } else {
                    h.jjtAccept(this, data);
                }
            }

            emitirPasoDelPara(node);
            if (inicioEtiquetado) {
                GeneradorCodigo.emitir("ir_a", "-", "-", etiquetaInicio);
            }
            GeneradorCodigo.etiquetar(etiquetaFin);
            return data;
        } finally {
            reutilizarAmbito = false;
            cerrarAmbitoAvisando();
        }
    }

    /** El I++ de la cabecera, emitido al final de la vuelta. */
    private void emitirPasoDelPara(NodoEasy node) {
        Token variable = node.tokenId;
        Token operador = node.operador(0);
        if (variable == null || operador == null) {
            return;   // cabecera incompleta por un error sintactico
        }
        String aritmetico =
            (operador.kind == EasyCompilerConstants.INCREMENTO_AL_VALOR) ? "+" : "-";
        GeneradorCodigo.emitir(aritmetico, variable.image, "1", variable.image);
    }

    /**
     * Evalua una condicion, exige que sea booleana y devuelve su atributo.
     *
     * La marca de la pila se toma y se restaura aqui: la condicion es una
     * expresion completa y no debe dejar operandos sueltos para la siguiente.
     * El 'lugar' sobrevive a la restauracion porque es una cadena, no una
     * entrada de la pila.
     */
    private Atributo condicionBooleana(NodoEasy nodoCondicion, String estructura) {
        int marca = pila.marca();
        try {
            Atributo c = evaluar(nodoCondicion);
            exigirBooleana(c, estructura, nodoCondicion);
            return c;
        } finally {
            pila.restaurar(marca);
        }
    }


    public Object visit(ASTImprimir node, Object data) {
        int marca = pila.marca();
        try {
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTExpresionConcatenada) {
                    Atributo a = evaluarValor(h);
                    if (!a.esError()) {
                        GeneradorCodigo.emitir("imprimir", a.lugar, "-", "-");
                    }
                } else {
                    h.jjtAccept(this, data);
                }
            }
            return data;
        } finally {
            pila.restaurar(marca);
        }
    }

    /**
     * LEER("mensaje" + Variable);
     *
     * Necesita al menos un sitio donde guardar lo que se lea. Se buscan los
     * identificadores del arbol de la expresion: si no hay ninguno, el LEER no
     * hace nada; si el que hay es una constante, no se puede escribir en el.
     */
    public Object visit(ASTLeer node, Object data) {
        int marca = pila.marca();
        boolean antes = dentroDeLeer;
        try {
            dentroDeLeer = true;
            node.childrenAccept(this, data);

            List<ASTValor> identificadores = new ArrayList<ASTValor>();
            recolectarIdentificadores(node, identificadores);

            if (identificadores.isEmpty()) {
                error("SEM-37", node.jjtGetFirstToken(),
                    "LEER necesita al menos una variable donde guardar el dato.",
                    "Usa LEER(Variable); o LEER(\"Dame un dato: \" + Variable);");
                return data;
            }

            for (int i = 0; i < identificadores.size(); i++) {
                comprobarDestinoDeLectura(identificadores.get(i));
            }
            return data;
        } finally {
            dentroDeLeer = antes;
            pila.restaurar(marca);
        }
    }

    private void comprobarDestinoDeLectura(ASTValor hoja) {
        Token t = hoja.tokenId;
        if (t == null) {
            return;
        }
        Simbolo s = TablaSimbolos.buscar(t.image);
        if (s == null) {
            return;   // el "no declarado" ya se reporto al recorrer la expresion
        }
        if ("CONSTANTE".equals(s.categoria)) {
            error("SEM-38", t,
                "LEER no puede escribir en '" + t.image + "' porque es una constante.",
                "Lee dentro de una variable, o dentro de un elemento de un arreglo.");
            return;
        }
        s.tieneValor = true;
        GeneradorCodigo.emitir("leer", "-", "-", t.image);
    }

    /** Recolecta las hojas ASTValor que son identificadores. */
    private void recolectarIdentificadores(NodoEasy node, List<ASTValor> acumulador) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            NodoEasy h = node.hijo(i);
            if (h == null) {
                continue;
            }
            if (h instanceof ASTValor) {
                ASTValor v = (ASTValor) h;
                if (v.tokenId != null && v.tokenId.kind == EasyCompilerConstants.VARIABLE) {
                    acumulador.add(v);
                } else if (v.tokenId != null && v.tokenId.kind == EasyCompilerConstants.CONSTANTE) {
                    acumulador.add(v);
                }
            }
            recolectarIdentificadores(h, acumulador);
        }
    }

    /**
     * SEGUN (Variable) { CASO valor: ... }
     *
     * Los valores de cada CASO son hijos ASTValor directos de este nodo, y
     * tienen que ser literales comparables con la variable de control.
     */
    public Object visit(ASTCondicionalSegun node, Object data) {
        int marca = pila.marca();
        try {
            Tipo tipoControl = Tipo.ERROR;
            Token ctrl = node.tokenId;
            if (ctrl != null) {
                Atributo c = resolver(ctrl);
                tipoControl = c.tipo;
                if (!c.esError() && !CuboSemantico.admitidoEnSegun(c.tipo)) {
                    error("SEM-30", ctrl,
                        "El SEGUN no admite la variable '" + ctrl.image + "', de tipo " + c.tipo + ".",
                        "La variable de control de un SEGUN debe ser ENT, LETRA o TXT.");
                    tipoControl = Tipo.ERROR;
                }
            }

            // TRADUCCION del SEGUN (Aho, seccion 6.8).
            //
            //          ir_a Lprueba
            //   Lc1:   S1
            //          ir_a Lfin
            //   Lc2:   S2
            //          ir_a Lfin
            //   Ldef:  Sd
            //          ir_a Lfin
            //   Lprueba: si X == v1 ir_a Lc1
            //            si X == v2 ir_a Lc2
            //            ir_a Ldef
            //   Lfin:
            //
            // Las pruebas se emiten al FINAL, juntas, y no antes de cada caso.
            // Puestas delante habria que saltar por encima de cada cuerpo, y el
            // codigo tendria el doble de saltos. Agrupadas quedan como una tabla
            // de decision, que es ademas lo que permitiria mas adelante
            // convertirlas en un salto indexado.
            String etiquetaPrueba = GeneradorCodigo.nuevaEtiqueta();
            String etiquetaFin = GeneradorCodigo.nuevaEtiqueta();
            String etiquetaDefecto = null;
            List<String> valoresDeCaso = new ArrayList<String>();
            List<String> etiquetasDeCaso = new ArrayList<String>();

            GeneradorCodigo.emitir("ir_a", "-", "-", etiquetaPrueba);

            List<String> yaVistos = new ArrayList<String>();
            boolean esperandoCuerpoDeCaso = false;
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                NodoEasy h = node.hijo(i);
                if (h == null) {
                    continue;
                }
                if (h instanceof ASTValor) {
                    comprobarCaso((ASTValor) h, tipoControl, yaVistos);
                    String etiquetaCaso = GeneradorCodigo.nuevaEtiqueta();
                    valoresDeCaso.add(((ASTValor) h).tokenId == null
                            ? "?" : ((ASTValor) h).tokenId.image);
                    etiquetasDeCaso.add(etiquetaCaso);
                    GeneradorCodigo.etiquetar(etiquetaCaso);
                    esperandoCuerpoDeCaso = true;
                } else if (h instanceof ASTSentencias) {
                    if (!esperandoCuerpoDeCaso) {
                        // Un bloque de sentencias sin CASO delante es el DEFECTO.
                        etiquetaDefecto = GeneradorCodigo.nuevaEtiqueta();
                        GeneradorCodigo.etiquetar(etiquetaDefecto);
                    }
                    h.jjtAccept(this, data);
                    // El DETENER; es obligatorio al final de cada CASO, asi que
                    // el salto de salida se emite siempre aqui.
                    GeneradorCodigo.emitir("ir_a", "-", "-", etiquetaFin);
                    esperandoCuerpoDeCaso = false;
                } else {
                    h.jjtAccept(this, data);
                }
            }

            GeneradorCodigo.etiquetar(etiquetaPrueba);
            String lugarControl = (ctrl == null) ? "?" : ctrl.image;
            for (int i = 0; i < valoresDeCaso.size(); i++) {
                GeneradorCodigo.emitir("si_igual", lugarControl,
                                       valoresDeCaso.get(i), etiquetasDeCaso.get(i));
            }
            GeneradorCodigo.emitir("ir_a", "-", "-",
                (etiquetaDefecto == null) ? etiquetaFin : etiquetaDefecto);
            GeneradorCodigo.etiquetar(etiquetaFin);
            return data;
        } finally {
            pila.restaurar(marca);
        }
    }

    private void comprobarCaso(ASTValor caso, Tipo tipoControl, List<String> yaVistos) {
        Token t = caso.tokenId;
        if (t == null) {
            return;
        }
        if (t.kind == EasyCompilerConstants.VARIABLE || t.kind == EasyCompilerConstants.CONSTANTE) {
            error("SEM-32", t,
                "El valor de un CASO debe ser un literal; '" + t.image + "' es un identificador.",
                "Escribe el valor directamente. Por ejemplo: CASO 1: o CASO 'A':");
            return;
        }

        Atributo lit = Atributo.deLiteral(t);
        caso.tipoInferido = lit.tipo;

        if (!tipoControl.esError()
                && CuboSemantico.resultado(tipoControl, "==", lit.tipo).esError()) {
            error("SEM-31", t,
                "El CASO " + t.image + " (" + lit.tipo
                    + ") no se puede comparar con la variable del SEGUN, que es " + tipoControl + ".",
                "Todos los CASO deben ser del mismo tipo que la variable del SEGUN.");
            return;
        }

        if (yaVistos.contains(t.image)) {
            error("SEM-33", t,
                "El valor " + t.image + " ya se habia usado en otro CASO.",
                "Cada CASO debe tener un valor distinto; si no, el segundo nunca se alcanza.");
            return;
        }
        yaVistos.add(t.image);
    }

    // =====================================================================
    // REGLAS SUELTAS
    // =====================================================================

    /** Busca el identificador; si no existe lo reporta una sola vez. */
    private Atributo resolver(Token id) {
        Simbolo s = TablaSimbolos.buscar(id.image);
        if (s == null) {
            error("SEM-10", id,
                "El identificador '" + id.image + "' no ha sido declarado.",
                "Declaralo antes de usarlo. En EasyScript la declaracion debe aparecer ANTES del primer uso.");
            marcarComoIndefinido(id);
            return Atributo.error(id);
        }
        s.usado = true;

        if (!s.tieneValor && !dentroDeLeer && !"INDEFINIDO".equals(s.categoria)) {
            advertencia("ADV-02", id.beginLine, id.beginColumn,
                "'" + id.image + "' se usa antes de recibir un valor.",
                "Dale un valor en la declaracion (por ejemplo ENT " + id.image
                    + " = 0;) o con un LEER antes de usarla.");
            // Una sola vez por simbolo: si esta dentro de un ciclo, avisar en
            // cada uso no aporta nada.
            s.tieneValor = true;
        }
        return Atributo.deSimbolo(id, s);
    }

    /**
     * Instala un hueco para un nombre no declarado.
     *
     * Sin esto, un nombre mal escrito usado en cinco sitios produce cinco veces
     * el mismo error. Con el hueco, los usos siguientes se resuelven en
     * silencio. TablaSimbolos sabe que un INDEFINIDO no cuenta como declaracion
     * y lo sustituye si la declaracion de verdad aparece mas adelante.
     */
    private void marcarComoIndefinido(Token id) {
        Simbolo s = TablaSimbolos.agregarTipo(id, Tipo.ERROR.lexema, "INDEFINIDO", "-", false, "-");
        if (s != null) {
            s.usado = true;
            s.tieneValor = true;
        }
    }

    /** Resuelve el destino de una asignacion y comprueba que se pueda escribir. */
    private Atributo resolverDestino(NodoEasy node, Token id) {
        Simbolo s = TablaSimbolos.buscar(id.image);
        if (s == null) {
            error("SEM-11", id,
                "No se puede asignar a '" + id.image + "' porque no ha sido declarado.",
                "Declara primero su tipo. Por ejemplo: ENT " + id.image + " = 0;");
            marcarComoIndefinido(id);
            return Atributo.error(id);
        }
        s.usado = true;

        if ("CONSTANTE".equals(s.categoria)) {
            error("SEM-12", id,
                "'" + id.image + "' es una constante y su valor no puede cambiar.",
                "Si necesitas que cambie, declarala como variable en lugar de usar el prefijo CONST_.");
            return Atributo.error(id);
        }

        return aplicarIndices(Atributo.deSimbolo(id, s), s, node.numIndices, id);
    }

    /** Comprueba que el numero de indices case con lo que se declaro. */
    private Atributo aplicarIndices(Atributo a, Simbolo s, int indices, Token donde) {
        int esperados = "ARREGLO".equals(s.categoria) ? 1
                      : "MATRIZ".equals(s.categoria) ? 2 : 0;

        if (indices == esperados) {
            return a;
        }
        if (esperados == 0) {
            error("SEM-16", donde,
                "'" + s.nombre + "' es una variable simple (" + s.tipo + ") y no admite indices.",
                "Quita los corchetes, o declarala como ARREGLO o MATRIZ.");
            return Atributo.error(donde);
        }
        if (indices == 0) {
            error("SEM-15", donde,
                "'" + s.nombre + "' es " + (esperados == 1 ? "un ARREGLO" : "una MATRIZ")
                    + " y debe usarse con " + (esperados == 1 ? "un indice" : "dos indices") + ".",
                "Indica el elemento. Por ejemplo: " + s.nombre
                    + (esperados == 1 ? "[0]" : "[0][0]") + ".");
            return Atributo.error(donde);
        }
        error("SEM-17", donde,
            "'" + s.nombre + "' necesita " + esperados + " indice(s) y se recibieron " + indices + ".",
            esperados == 2 ? "Una MATRIZ se accede como Matriz[fila][columna]."
                           : "Un ARREGLO se accede con un solo indice: Arreglo[i].");
        return Atributo.error(donde);
    }

    /** Aplica los indices de una lectura dentro de una expresion. */
    private Atributo accesoIndexado(Atributo base, NodoEasy node) {
        if (base.esError() || base.simbolo == null) {
            return base;   // literales y operandos ya invalidos pasan tal cual
        }
        return aplicarIndices(base, base.simbolo, node.numIndices, base.token);
    }

    /** El indice de un arreglo o matriz tiene que ser entero. */
    private void comprobarIndice(Atributo indice) {
        if (indice == null || indice.esError()) {
            return;
        }
        if (indice.tipo != Tipo.ENT) {
            error("SEM-18", indice.token,
                "El indice debe ser ENT; la expresion da " + indice.tipo + ".",
                "Usa una expresion entera como indice.");
        }
    }

    /**
     * Indice literal fuera del rango declarado.
     *
     * Solo se puede comprobar cuando el indice es una constante escrita en el
     * codigo: con una variable habria que ejecutar el programa.
     */
    private void comprobarRango(Atributo indice, Simbolo s, int posicion) {
        if (indice == null || indice.esError() || s == null || !indice.esConstanteConocida()) {
            return;
        }
        int limite = (posicion == 1) ? s.filas : s.columnas;
        if (limite <= 0) {
            return;   // no se conocio el tamano declarado
        }
        int v;
        try {
            v = Integer.parseInt(indice.valorConstante.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (v < 0 || v >= limite) {
            error("SEM-19", indice.token,
                "El indice " + v + " esta fuera del rango valido de '" + s.nombre
                    + "' (0 a " + (limite - 1) + ").",
                "Los indices van de 0 a tamano-1. Un arreglo de " + limite
                    + " elementos llega hasta el " + (limite - 1) + ".");
        }
    }

    private void comprobarAsignacion(Atributo destino, Atributo origen, Token donde) {
        if (destino.esError() || origen.esError()) {
            return;
        }
        if (CuboSemantico.asignable(destino.tipo, origen.tipo) == CuboSemantico.ASIGNACION_ERROR) {
            if (destino.tipo == Tipo.ENT && origen.tipo == Tipo.DEC) {
                error("SEM-04", donde,
                    "Asignar un valor DEC a '" + destino.lexema + "' (ENT) perderia los decimales.",
                    "Declara '" + destino.lexema + "' como DEC, o usa una expresion entera.");
            } else {
                error("SEM-29", donde,
                    "No se puede asignar un valor " + origen.tipo + " a '"
                        + destino.lexema + "', que es " + destino.tipo + ".",
                    "Iguala los tipos, o cambia la declaracion de '" + destino.lexema + "'.");
            }
        }
    }

    private void comprobarIncremento(Atributo destino, Token op) {
        if (destino.esError()) {
            return;
        }
        if (!destino.tipo.esNumerico()) {
            error("SEM-14", op,
                "El operador '" + op.image + "' solo se aplica a valores ENT o DEC; '"
                    + destino.lexema + "' es " + destino.tipo + ".",
                "Incrementa solo contadores numericos.");
        }
    }

    private void exigirBooleana(Atributo c, String estructura, NodoEasy donde) {
        if (c == null || c.esError()) {
            return;
        }
        if (c.tipo != Tipo.BOOL) {
            Token t = (c.token != null) ? c.token : donde.jjtGetFirstToken();
            error("SEM-28", t,
                "La condicion del " + estructura + " debe ser BOOL; la expresion da " + c.tipo + ".",
                "EasyScript no acepta numeros como condicion. Escribe una comparacion, por ejemplo: (Numero != 0).");
            return;
        }

        // Una condicion escrita como VERDADERO o FALSO no depende de nada: el
        // bloque se ejecuta siempre o no se ejecuta nunca. Casi siempre es un
        // resto de una prueba que se quedo en el codigo.
        if (c.esLiteral && c.token != null) {
            boolean siempreFalsa = c.token.kind == EasyCompilerConstants.FALSO_BOOLEANO;
            String efecto = siempreFalsa
                    ? "el bloque nunca se ejecuta"
                    : ("MIENTRAS".equals(estructura) ? "el ciclo no termina" : "el bloque se ejecuta siempre");
            advertencia("ADV-04", c.token.beginLine, c.token.beginColumn,
                "La condicion del " + estructura + " es constante: " + efecto + ".",
                "Revisa la condicion; puede que quisieras comparar dos valores.");
        }
    }

    /**
     * Un literal entero tiene que caber en un ENT.
     *
     * Se comprueba sobre el texto y no sobre el valor porque el escaner acepta
     * cualquier cantidad de digitos: sin esto, un numero enorme entraria en la
     * tabla de simbolos como si nada y fallaria mucho mas tarde.
     */
    private void comprobarRangoDelLiteral(Token t) {
        if (t.kind != EasyCompilerConstants.NUM_ENTERO) {
            return;
        }
        try {
            Integer.parseInt(t.image);
        } catch (NumberFormatException e) {
            error("SEM-34", t,
                "El numero " + t.image + " no cabe en un ENT (el maximo es " + Integer.MAX_VALUE + ").",
                "Usa DEC si necesitas valores mas grandes, o revisa si sobran digitos.");
        }
    }

    /** Division o modulo entre un cero escrito literalmente. */
    private void comprobarDivisionEntreCero(Token op, Atributo divisor) {
        if (!"/".equals(op.image) && !"%".equals(op.image)) {
            return;
        }
        if (!divisor.esConstanteConocida()) {
            return;
        }
        String v = divisor.valorConstante.trim();
        if (esCero(v)) {
            error("SEM-22", op,
                "Division entre cero: el divisor constante es " + v + ".",
                "Revisa el divisor. Si deberia ser una variable, validala con un SI antes de dividir.");
        }
    }

    private boolean esCero(String v) {
        try {
            return Double.parseDouble(v) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void reportarRelacionalInvalida(Token op, Atributo izq, Atributo der) {
        boolean ordenSobreTexto = (izq.tipo == Tipo.TXT || der.tipo == Tipo.TXT)
                && !"==".equals(op.image) && !"!=".equals(op.image);
        if (ordenSobreTexto) {
            error("SEM-24", op,
                "Los valores TXT no se pueden ordenar con '" + op.image + "'.",
                "Para texto usa solo '==' o '!='.");
        } else {
            error("SEM-23", op,
                "El operador '" + op.image + "' no puede comparar " + izq.tipo + " con " + der.tipo + ".",
                "Compara valores del mismo tipo: ENT y DEC entre si, LETRA con LETRA.");
        }
    }

    // =====================================================================
    // UTILIDADES DE RECORRIDO
    // =====================================================================

    /**
     * Visita un hijo de expresion y garantiza que deje exactamente un operando
     * en la pila.
     *
     * La red de seguridad importa: la recuperacion de errores sintacticos deja
     * nodos con menos hijos de los que su produccion promete, y sin esto un
     * hueco se arrastraria por toda la expresion.
     */
    private void evaluarYApilar(NodoEasy hijo) {
        if (hijo == null) {
            pila.apilar(Atributo.error(null));
            return;
        }
        int antes = pila.tamano();
        hijo.jjtAccept(this, null);
        if (pila.tamano() == antes + 1) {
            return;
        }
        pila.restaurar(antes);
        pila.apilar(Atributo.error(hijo.jjtGetFirstToken()));
    }

    /** Como evaluarYApilar, pero devolviendo el operando en vez de dejarlo. */
    private Atributo evaluar(NodoEasy hijo) {
        evaluarYApilar(hijo);
        return pila.desapilar();
    }

    /**
     * Token del operador relacional de una CondicionSimple, o null si no habia
     * comparacion. Vive en el nodo hijo ASTOperadorRelacional, que la gramatica
     * decoro con su propio token.
     */
    private Token operadorRelacionalDe(NodoEasy node) {
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            NodoEasy h = node.hijo(i);
            if (h instanceof ASTOperadorRelacional) {
                return h.tokenId;
            }
        }
        return null;
    }

    // =====================================================================
    // DIAGNOSTICO
    // =====================================================================

    private void error(String codigo, Token donde, String detalle, String consejo) {
        int linea = (donde == null) ? -1 : donde.beginLine;
        int columna = (donde == null) ? -1 : donde.beginColumn;
        EasyCompiler.listaErrores.add(
            new ErrorCompilador("Semántico", codigo, linea, columna, detalle, consejo));
    }

    private void advertencia(String codigo, int linea, int columna, String detalle, String consejo) {
        EasyCompiler.listaErrores.add(
            new ErrorCompilador("Advertencia", codigo, linea, columna, detalle, consejo));
    }
}
