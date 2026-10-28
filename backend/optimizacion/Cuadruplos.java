// Archivo: Cuadruplos.java
//
// Lo que todas las fases necesitan saber de un cuadruplo: que nombres lee, cual
// escribe, si es un salto, y si una casilla es un literal, un temporal o una
// variable. Centralizarlo evita que cada fase lo deduzca a su manera.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.List;

import backend.Cuadruplo;

final class Cuadruplos {

    private Cuadruplos() {
    }

    // ------------------------------------------------------------------
    // Casillas
    // ------------------------------------------------------------------

    /** Casilla vacia: "-" (o null, por si acaso). */
    static boolean vacia(String s) {
        return s == null || "-".equals(s);
    }

    /** Literal escrito tal cual: numero, texto, letra o booleano. */
    static boolean esLiteral(String s) {
        if (vacia(s)) {
            return false;
        }
        return s.startsWith("\"") || s.startsWith("'")
            || "VERDADERO".equals(s) || "FALSO".equals(s)
            || s.matches("-?\\d+") || s.matches("-?\\d*\\.\\d+");
    }

    /** Temporal del generador o del optimizador: t1, t2, ... */
    static boolean esTemporal(String s) {
        return s != null && s.matches("t\\d+");
    }

    /** Nombre que vive en memoria: variable, constante, arreglo o temporal. */
    static boolean esNombre(String s) {
        return !vacia(s) && !esLiteral(s);
    }

    // ------------------------------------------------------------------
    // Operadores
    // ------------------------------------------------------------------

    static boolean esEtiqueta(Cuadruplo c) {
        return "etiqueta".equals(c.operador);
    }

    static boolean esSalto(Cuadruplo c) {
        return "ir_a".equals(c.operador) || c.operador.startsWith("si_");
    }

    static boolean esSaltoCondicional(Cuadruplo c) {
        return c.operador.startsWith("si_");
    }

    /**
     * Operadores que solo calculan un valor a partir de sus operandos y lo dejan
     * en 'resultado', sin otro efecto: son los que se pueden plegar, fusionar,
     * mover o eliminar si el resultado no se usa.
     */
    static boolean esCalculo(Cuadruplo c) {
        String op = c.operador;
        return !esEtiqueta(c) && !esSalto(c)
            && !"[]=".equals(op) && !"imprimir".equals(op) && !"leer".equals(op);
    }

    // ------------------------------------------------------------------
    // Usos y definiciones
    // ------------------------------------------------------------------

    /**
     * Nombres que la instruccion LEE. En "[]=" (arg1[arg2] = res) se leen el
     * desplazamiento y el valor; el arreglo no se lee, se escribe en parte.
     */
    static List<String> usos(Cuadruplo c) {
        List<String> u = new ArrayList<String>();
        String op = c.operador;
        if (esEtiqueta(c) || "ir_a".equals(op) || "leer".equals(op)) {
            return u;
        }
        if ("[]=".equals(op)) {
            agregarNombre(u, c.arg2);
            agregarNombre(u, c.resultado);
            return u;
        }
        agregarNombre(u, c.arg1);
        agregarNombre(u, c.arg2);
        return u;
    }

    /**
     * Nombre que la instruccion ESCRIBE por completo, o null. Una escritura
     * indexada no cuenta: cambia un elemento, no el arreglo entero (ver
     * arregloEscrito).
     */
    static String definicion(Cuadruplo c) {
        if (esEtiqueta(c) || esSalto(c) || "[]=".equals(c.operador) || "imprimir".equals(c.operador)) {
            return null;
        }
        return esNombre(c.resultado) ? c.resultado : null;
    }

    /** El arreglo que modifica una escritura indexada, o null. */
    static String arregloEscrito(Cuadruplo c) {
        return "[]=".equals(c.operador) ? c.arg1 : null;
    }

    private static void agregarNombre(List<String> lista, String s) {
        if (esNombre(s)) {
            lista.add(s);
        }
    }

    // ------------------------------------------------------------------
    // Listas de cuadruplos
    // ------------------------------------------------------------------

    /** Copia profunda: el optimizador nunca toca la lista del generador. */
    static List<Cuadruplo> copiar(List<Cuadruplo> codigo) {
        List<Cuadruplo> copia = new ArrayList<Cuadruplo>();
        for (Cuadruplo c : codigo) {
            copia.add(new Cuadruplo(c.indice, c.operador, c.arg1, c.arg2, c.resultado, c.comentario));
        }
        return copia;
    }

    /** Deja cada indice igual a su posicion, despues de borrar o insertar. */
    static void renumerar(List<Cuadruplo> codigo) {
        for (int i = 0; i < codigo.size(); i++) {
            codigo.get(i).indice = i;
        }
    }

    /** Saltos a etiquetas que no existen (debe quedar vacia). */
    static List<String> etiquetasRotas(List<Cuadruplo> codigo) {
        List<String> definidas = new ArrayList<String>();
        for (Cuadruplo c : codigo) {
            if (esEtiqueta(c)) {
                definidas.add(c.resultado);
            }
        }
        List<String> rotas = new ArrayList<String>();
        for (Cuadruplo c : codigo) {
            if (esSalto(c) && !definidas.contains(c.resultado) && !rotas.contains(c.resultado)) {
                rotas.add(c.resultado);
            }
        }
        return rotas;
    }

    /** Linea con las cuatro casillas y la forma legible, para la bitacora y los archivos. */
    static String linea(Cuadruplo c) {
        return c.indice + " | " + c.operador + " | " + c.arg1 + " | " + c.arg2
            + " | " + c.resultado + " | " + c.comentario;
    }
}
