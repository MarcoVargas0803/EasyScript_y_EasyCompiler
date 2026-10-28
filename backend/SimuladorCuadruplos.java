// Archivo: SimuladorCuadruplos.java
//
// Interprete de cuadruplos. Ejecuta el codigo de tres direcciones que dejo
// GeneradorCodigo, sin pasar por ninguna maquina real.
//
// Para que sirve
// --------------
// Es la red de seguridad de la Unidad 2. Las etapas siguientes van a tocar el
// generador (conversiones a texto, saltos fusionados, renumeracion de
// temporales y etiquetas...) y la unica forma honesta de saber que no se rompio
// nada es EJECUTAR el codigo antes y despues y comparar lo que hace. Comparar
// el texto de los cuadruplos no sirve: cambia a proposito en cada etapa.
//
// Por eso la traza esta pensada para NO depender de nombres internos:
//   - no aparecen los temporales (t1, t2...) ni las etiquetas (L1, L2...),
//   - no aparece el numero de pasos (los saltos fusionados lo reducen),
//   - el estado final solo incluye variables del usuario.
// Si dos versiones del generador producen la misma traza, el programa hace lo
// mismo con ambas, aunque los cuadruplos sean distintos.
//
// Modelo de memoria
// -----------------
// Un mapa nombre -> valor para variables y temporales, y otro mapa
// nombre -> (desplazamiento en bytes -> valor) para arreglos y matrices. Los
// desplazamientos se guardan tal cual los calcula el generador (indice por
// ancho del elemento); el simulador no necesita saber el ancho.
//
// Los valores son objetos Java: Long (ENT), Double (DEC), Boolean (BOOL),
// Character (LETRA) y String (TXT). Un nombre que todavia no tiene valor vale
// 0, igual que una variable ENT recien reservada.
//
// La entrada (LEER) no es interactiva: sale de una secuencia fija y ciclica.
// Asi la simulacion es determinista y se puede comparar entre ejecuciones.
package backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class SimuladorCuadruplos {

    /** Pasos por omision antes de cortar. Hay pruebas con MIENTRAS (VERDADERO). */
    public static final int LIMITE_POR_OMISION = 20000;

    /** Lo que "teclea" el usuario en cada LEER, en este orden y en ciclo. */
    private static final long[] ENTRADAS = { 3, 5, 1, 0, 2, 4 };

    private final List<Cuadruplo> codigo;
    private final int limitePasos;

    private final Map<String, Object> memoria = new HashMap<String, Object>();
    private final Map<String, TreeMap<Long, Object>> arreglos =
        new HashMap<String, TreeMap<Long, Object>>();
    private final Map<String, Integer> etiquetas = new HashMap<String, Integer>();

    private final List<String> traza = new ArrayList<String>();
    private int pasos = 0;

    // Contadores para las metricas de la optimizacion. No van a la traza: solo
    // se consultan con sus getters, asi que la traza sigue igual que antes.
    private final Map<String, Integer> ejecucionesPorOperador = new TreeMap<String, Integer>();
    private long lecturasMemoria = 0;
    private long escriturasMemoria = 0;
    private int siguienteEntrada = 0;
    private String terminacion = null;
    private int indiceDelFallo = -1;

    public SimuladorCuadruplos(List<Cuadruplo> codigo) {
        this(codigo, LIMITE_POR_OMISION);
    }

    public SimuladorCuadruplos(List<Cuadruplo> codigo, int limitePasos) {
        this.codigo = codigo;
        this.limitePasos = limitePasos;
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    /** Instrucciones ejecutadas. Va aparte de la traza a proposito: ver cabecera. */
    public int getPasos() {
        return pasos;
    }

    public List<String> getTraza() {
        return traza;
    }

    /** Cuantas veces se ejecuto cada operador. Las etiquetas no cuentan. */
    public Map<String, Integer> getEjecucionesPorOperador() {
        return ejecucionesPorOperador;
    }

    /** Lecturas de variables, temporales y elementos (los literales no leen memoria). */
    public long getLecturasMemoria() {
        return lecturasMemoria;
    }

    /** Escrituras de variables, temporales y elementos. */
    public long getEscriturasMemoria() {
        return escriturasMemoria;
    }

    /** "fin", "corte: limite de pasos", "error de ejecucion: ..." o "operador desconocido: X". */
    public String getTerminacion() {
        return terminacion;
    }

    /** Indice del cuadruplo que provoco el error o el operador desconocido; -1 si no hubo. */
    public int getIndiceDelFallo() {
        return indiceDelFallo;
    }

    // ------------------------------------------------------------------
    // Ejecucion
    // ------------------------------------------------------------------

    /** Error al ejecutar: division entre cero, tipos que no casan, etc. */
    private static class ErrorEjecucion extends RuntimeException {
        ErrorEjecucion(String mensaje) {
            super(mensaje);
        }
    }

    /** Operador que el simulador no sabe ejecutar. Detiene la simulacion. */
    private static class OperadorDesconocido extends RuntimeException {
        OperadorDesconocido(String operador) {
            super(operador);
        }
    }

    /** Ejecuta el codigo completo y devuelve la traza. Solo se debe llamar una vez. */
    public List<String> ejecutar() {
        // Las etiquetas se resuelven antes de empezar: un salto hacia adelante
        // tiene que conocer su destino aunque todavia no se haya pasado por el.
        for (int i = 0; i < codigo.size(); i++) {
            Cuadruplo c = codigo.get(i);
            if ("etiqueta".equals(c.operador) && !etiquetas.containsKey(c.resultado)) {
                etiquetas.put(c.resultado, Integer.valueOf(i));
            }
        }

        int pc = 0;
        try {
            while (pc < codigo.size()) {
                if (pasos >= limitePasos) {
                    terminacion = "corte: limite de pasos";
                    break;
                }
                pasos++;
                indiceDelFallo = pc;
                pc = ejecutarUna(codigo.get(pc), pc);
            }
            if (terminacion == null) {
                terminacion = "fin";
            }
            indiceDelFallo = -1;   // terminar o cortar no es un fallo de ninguna instruccion
        } catch (ErrorEjecucion e) {
            terminacion = "error de ejecucion: " + e.getMessage();
        } catch (OperadorDesconocido e) {
            terminacion = "operador desconocido: " + e.getMessage();
        }

        traza.add(terminacion);
        volcarEstadoFinal();
        return traza;
    }

    /** Ejecuta un cuadruplo y devuelve el indice de la siguiente instruccion. */
    private int ejecutarUna(Cuadruplo c, int pc) {
        String op = c.operador;
        int siguiente = pc + 1;

        if ("etiqueta".equals(op)) {
            return siguiente;
        }
        Integer veces = ejecucionesPorOperador.get(op);
        ejecucionesPorOperador.put(op, Integer.valueOf(veces == null ? 1 : veces.intValue() + 1));
        if ("=".equals(op)) {
            asignar(c.resultado, valor(c.arg1));
            return siguiente;
        }
        if (esUnario(op)) {
            asignar(c.resultado, evaluar(op, valor(c.arg1), null));
            return siguiente;
        }
        if (esBinario(op)) {
            asignar(c.resultado, evaluar(op, valor(c.arg1), valor(c.arg2)));
            return siguiente;
        }
        if ("ir_a".equals(op)) {
            return destino(c.resultado);
        }
        if ("si_falso".equals(op)) {
            return booleano(valor(c.arg1)) ? siguiente : destino(c.resultado);
        }
        if ("si_verdadero".equals(op)) {
            return booleano(valor(c.arg1)) ? destino(c.resultado) : siguiente;
        }
        if ("si_igual".equals(op)) {
            return comparar("==", valor(c.arg1), valor(c.arg2)) ? destino(c.resultado) : siguiente;
        }
        if (op.startsWith("si_")) {
            // Salto fusionado: (si_<, y, z, L) compara y salta en una sola
            // instruccion. Se reconoce por el prefijo para no tener que
            // listar cada operador relacional dos veces.
            String relop = op.substring(3);
            if (!esRelacional(relop)) {
                throw new OperadorDesconocido(op);
            }
            return comparar(relop, valor(c.arg1), valor(c.arg2)) ? destino(c.resultado) : siguiente;
        }
        if ("=[]".equals(op)) {
            TreeMap<Long, Object> arreglo = arreglos.get(c.arg1);
            Object v = (arreglo == null) ? null : arreglo.get(Long.valueOf(desplazamiento(c.arg2)));
            lecturasMemoria++;
            asignar(c.resultado, (v == null) ? Long.valueOf(0) : v);
            return siguiente;
        }
        if ("[]=".equals(op)) {
            // El valor a guardar va en la casilla 'resultado', no en arg1: ver
            // GeneradorCodigo.legible(), que lo escribe "arg1[arg2] = res".
            TreeMap<Long, Object> arreglo = arreglos.get(c.arg1);
            if (arreglo == null) {
                arreglo = new TreeMap<Long, Object>();
                arreglos.put(c.arg1, arreglo);
            }
            arreglo.put(Long.valueOf(desplazamiento(c.arg2)), valor(c.resultado));
            escriturasMemoria++;
            return siguiente;
        }
        if ("imprimir".equals(op)) {
            traza.add("imprimir: " + texto(valor(c.arg1)));
            return siguiente;
        }
        if ("leer".equals(op)) {
            Long v = Long.valueOf(ENTRADAS[siguienteEntrada % ENTRADAS.length]);
            siguienteEntrada++;
            asignar(c.resultado, v);
            // Un LEER sobre un elemento de arreglo lee a un temporal y luego lo
            // guarda con []=. El nombre del temporal no va a la traza: las
            // etapas siguientes lo renumeran, y donde acabo el dato ya se ve en
            // el estado final.
            String destino = c.resultado.matches("t\\d+") ? "<temporal>" : c.resultado;
            traza.add("leer " + destino + " <- " + texto(v));
            return siguiente;
        }
        throw new OperadorDesconocido(op);
    }

    // ------------------------------------------------------------------
    // Operandos
    // ------------------------------------------------------------------

    /**
     * Valor de una casilla del cuadruplo: un literal escrito tal cual o el
     * nombre de una variable o temporal.
     */
    private Object valor(String casilla) {
        if (casilla == null || "-".equals(casilla)) {
            throw new ErrorEjecucion("falta un operando");
        }
        Object literal = valorLiteral(casilla);
        if (literal != null) {
            return literal;
        }
        Object v = memoria.get(casilla);
        lecturasMemoria++;
        return (v == null) ? Long.valueOf(0) : v;
    }

    /**
     * Valor de un literal escrito en una casilla ("texto", 'a', VERDADERO, 12,
     * 3.5), o null si la casilla no es un literal. Lo usa tambien el
     * optimizador para leer los literales igual que se ejecutan.
     */
    public static Object valorLiteral(String casilla) {
        if (casilla == null || "-".equals(casilla)) {
            return null;
        }
        if (casilla.startsWith("\"")) {
            return desescaparTexto(casilla);
        }
        if (casilla.startsWith("'")) {
            return desescaparLetra(casilla);
        }
        if ("VERDADERO".equals(casilla)) {
            return Boolean.TRUE;
        }
        if ("FALSO".equals(casilla)) {
            return Boolean.FALSE;
        }
        if (casilla.matches("-?\\d+")) {
            try {
                return Long.valueOf(casilla);
            } catch (NumberFormatException e) {
                throw new ErrorEjecucion("entero fuera de rango: " + casilla);
            }
        }
        if (casilla.matches("-?\\d*\\.\\d+") || casilla.matches("-?\\d+\\.\\d+")) {
            return Double.valueOf(casilla);
        }
        return null;
    }

    private void asignar(String nombre, Object v) {
        memoria.put(nombre, v);
        escriturasMemoria++;
    }

    private int destino(String etiqueta) {
        Integer i = etiquetas.get(etiqueta);
        if (i == null) {
            throw new ErrorEjecucion("salto a una etiqueta que no existe (" + etiqueta + ")");
        }
        return i.intValue();
    }

    private long desplazamiento(String casilla) {
        Object v = valor(casilla);
        if (v instanceof Long) {
            return ((Long) v).longValue();
        }
        throw new ErrorEjecucion("desplazamiento no entero (" + texto(v) + ")");
    }

    private static boolean booleano(Object v) {
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        throw new ErrorEjecucion("se esperaba un valor BOOL y se recibio " + texto(v));
    }

    /** "texto" -> texto, resolviendo los escapes que admite el lenguaje. */
    private static String desescaparTexto(String literal) {
        String cuerpo = literal;
        if (cuerpo.length() >= 2 && cuerpo.endsWith("\"")) {
            cuerpo = cuerpo.substring(1, cuerpo.length() - 1);
        } else {
            cuerpo = cuerpo.substring(1);
        }
        return desescapar(cuerpo);
    }

    private static Character desescaparLetra(String literal) {
        String cuerpo = literal;
        if (cuerpo.length() >= 2 && cuerpo.endsWith("'")) {
            cuerpo = cuerpo.substring(1, cuerpo.length() - 1);
        } else {
            cuerpo = cuerpo.substring(1);
        }
        String s = desescapar(cuerpo);
        if (s.isEmpty()) {
            throw new ErrorEjecucion("letra vacia: " + literal);
        }
        return Character.valueOf(s.charAt(0));
    }

    private static String desescapar(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) {
                sb.append(ch);
                continue;
            }
            char sig = s.charAt(i + 1);
            switch (sig) {
                case 'n':  sb.append('\n'); i++; break;
                case 't':  sb.append('\t'); i++; break;
                case 'r':  sb.append('\r'); i++; break;
                case '\\': sb.append('\\'); i++; break;
                case '"':  sb.append('"');  i++; break;
                case '\'': sb.append('\''); i++; break;
                default:   sb.append(ch);   break;   // escape desconocido: se deja como esta
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Operaciones
    // ------------------------------------------------------------------

    private static boolean esUnario(String op) {
        return "-u".equals(op) || "!!".equals(op) || "ampliar".equals(op) || "a_texto".equals(op);
    }

    private static boolean esBinario(String op) {
        return esAritmetico(op) || esRelacional(op) || "&&".equals(op) || "||".equals(op)
            || "concat".equals(op);
    }

    /**
     * Calcula el resultado de un operador sobre valores ya conocidos (b es null
     * en los unarios). Es la unica definicion de lo que hace cada operador: la
     * usan la ejecucion y el optimizador cuando calcula al compilar, asi que un
     * valor plegado es exactamente el que se obtendria al ejecutar.
     *
     * Lanza una RuntimeException si la operacion falla (division entre cero,
     * tipos que no casan) o IllegalArgumentException si el operador no calcula
     * un valor.
     */
    public static Object evaluar(String op, Object a, Object b) {
        if (esAritmetico(op)) {
            return aritmetica(op, a, b);
        }
        if (esRelacional(op)) {
            return Boolean.valueOf(comparar(op, a, b));
        }
        if ("&&".equals(op) || "||".equals(op)) {
            boolean x = booleano(a);
            boolean y = booleano(b);
            return Boolean.valueOf("&&".equals(op) ? (x && y) : (x || y));
        }
        if ("-u".equals(op)) {
            if (a instanceof Long) {
                return Long.valueOf(-((Long) a).longValue());
            } else if (a instanceof Double) {
                return Double.valueOf(-((Double) a).doubleValue());
            }
            throw new ErrorEjecucion("menos unario sobre un valor no numerico (" + texto(a) + ")");
        }
        if ("!!".equals(op)) {
            return Boolean.valueOf(!booleano(a));
        }
        if ("ampliar".equals(op)) {
            if (a instanceof Long) {
                return Double.valueOf(((Long) a).doubleValue());
            } else if (a instanceof Double) {
                return a;
            }
            throw new ErrorEjecucion("ampliar sobre un valor no numerico (" + texto(a) + ")");
        }
        if ("a_texto".equals(op)) {
            return texto(a);
        }
        if ("concat".equals(op)) {
            return texto(a) + texto(b);
        }
        throw new IllegalArgumentException("el operador no calcula un valor: " + op);
    }

    /**
     * Resultado de un salto condicional con operandos ya conocidos: true si
     * salta. (si_falso, si_verdadero, si_igual y si_<relop>.)
     */
    public static boolean saltaria(String op, Object a, Object b) {
        if ("si_falso".equals(op)) {
            return !booleano(a);
        }
        if ("si_verdadero".equals(op)) {
            return booleano(a);
        }
        if ("si_igual".equals(op)) {
            return comparar("==", a, b);
        }
        String relop = op.substring(3);
        if (!esRelacional(relop)) {
            throw new IllegalArgumentException("salto desconocido: " + op);
        }
        return comparar(relop, a, b);
    }

    private static boolean esAritmetico(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op)
            || "/".equals(op) || "%".equals(op) || "**".equals(op);
    }

    private static boolean esRelacional(String op) {
        return ">".equals(op) || "<".equals(op) || ">=".equals(op)
            || "<=".equals(op) || "==".equals(op) || "!=".equals(op);
    }

    /**
     * ENT con ENT da ENT (con division entera); en cuanto aparece un DEC el
     * resultado es DEC. Es la misma regla del cubo semantico, asi que el valor
     * simulado tiene el tipo que el compilador le asigno al temporal.
     */
    private static Object aritmetica(String op, Object a, Object b) {
        if (!(a instanceof Long || a instanceof Double) || !(b instanceof Long || b instanceof Double)) {
            throw new ErrorEjecucion("operacion '" + op + "' entre valores no numericos ("
                + texto(a) + ", " + texto(b) + ")");
        }
        if (a instanceof Long && b instanceof Long) {
            long x = ((Long) a).longValue();
            long y = ((Long) b).longValue();
            if (("/".equals(op) || "%".equals(op)) && y == 0) {
                throw new ErrorEjecucion("division entre cero");
            }
            switch (op) {
                case "+":  return Long.valueOf(x + y);
                case "-":  return Long.valueOf(x - y);
                case "*":  return Long.valueOf(x * y);
                case "/":  return Long.valueOf(x / y);
                case "%":  return Long.valueOf(x % y);
                default:   return Long.valueOf((long) Math.pow(x, y));
            }
        }
        double x = ((Number) a).doubleValue();
        double y = ((Number) b).doubleValue();
        if (("/".equals(op) || "%".equals(op)) && y == 0.0) {
            throw new ErrorEjecucion("division entre cero");
        }
        switch (op) {
            case "+":  return Double.valueOf(x + y);
            case "-":  return Double.valueOf(x - y);
            case "*":  return Double.valueOf(x * y);
            case "/":  return Double.valueOf(x / y);
            case "%":  return Double.valueOf(x % y);
            default:   return Double.valueOf(Math.pow(x, y));
        }
    }

    /**
     * Comparacion. Los numeros se comparan por valor aunque uno sea ENT y otro
     * DEC; las letras por su codigo; la igualdad vale ademas entre textos y
     * entre booleanos.
     */
    private static boolean comparar(String op, Object a, Object b) {
        int cmp;
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double)) {
            if (a instanceof Long && b instanceof Long) {
                cmp = Long.compare(((Long) a).longValue(), ((Long) b).longValue());
            } else {
                cmp = Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
            }
        } else if (a instanceof Character && b instanceof Character) {
            cmp = Character.compare(((Character) a).charValue(), ((Character) b).charValue());
        } else if ("==".equals(op) || "!=".equals(op)) {
            boolean iguales = a.getClass().equals(b.getClass()) && a.equals(b);
            return "==".equals(op) ? iguales : !iguales;
        } else if (a instanceof String && b instanceof String) {
            cmp = ((String) a).compareTo((String) b);
        } else {
            throw new ErrorEjecucion("comparacion '" + op + "' entre valores incompatibles ("
                + texto(a) + ", " + texto(b) + ")");
        }
        switch (op) {
            case ">":  return cmp > 0;
            case "<":  return cmp < 0;
            case ">=": return cmp >= 0;
            case "<=": return cmp <= 0;
            case "==": return cmp == 0;
            default:   return cmp != 0;
        }
    }

    /** Forma escrita de un valor: como lo mostraria IMPRIMIR. */
    public static String texto(Object v) {
        if (v == null) {
            return "0";
        }
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue() ? "VERDADERO" : "FALSO";
        }
        return v.toString();
    }

    // ------------------------------------------------------------------
    // Estado final
    // ------------------------------------------------------------------

    /**
     * Agrega a la traza el contenido de la memoria, solo de nombres del
     * usuario y en orden alfabetico. Los temporales se excluyen porque las
     * etapas siguientes los renumeran y porque no son parte del programa.
     */
    private void volcarEstadoFinal() {
        TreeSet<String> nombres = new TreeSet<String>();
        nombres.addAll(memoria.keySet());
        nombres.addAll(arreglos.keySet());
        for (String nombre : nombres) {
            if (nombre.matches("t\\d+")) {
                continue;
            }
            if (memoria.containsKey(nombre)) {
                traza.add(nombre + " = " + texto(memoria.get(nombre)));
            }
            TreeMap<Long, Object> arreglo = arreglos.get(nombre);
            if (arreglo != null) {
                for (Map.Entry<Long, Object> e : arreglo.entrySet()) {
                    traza.add(nombre + "[" + e.getKey() + "] = " + texto(e.getValue()));
                }
            }
        }
    }
}
