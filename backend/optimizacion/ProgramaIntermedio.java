// Archivo: ProgramaIntermedio.java
//
// El codigo que se esta optimizando, junto con lo que las fases necesitan saber
// de sus nombres y que los cuadruplos no dicen: el tipo de cada variable y
// temporal, que nombres son constantes y cuales arreglos. Tambien reparte
// nombres nuevos (temporales y etiquetas) que no choquen con los existentes.
package backend.optimizacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import backend.Cuadruplo;
import backend.ErrorCompilador;
import backend.GeneradorCodigo;
import backend.Simbolo;
import backend.TablaSimbolos;

final class ProgramaIntermedio {

    /** El codigo en optimizacion. Las fases lo modifican en su lugar. */
    final List<Cuadruplo> codigo;

    /** Tipo (lexema: ENT, DEC...) de cada variable y temporal conocido. */
    final Map<String, String> tipos = new HashMap<String, String>();

    /** Constantes (CONST_...): se asignan una sola vez y nunca cambian. */
    final Set<String> constantes = new HashSet<String>();

    /** Arreglos y matrices, por su nombre de codigo. */
    final Set<String> arreglos = new HashSet<String>();

    /** Advertencias que descubre la optimizacion (ej. division entre cero constante). */
    final List<ErrorCompilador> advertencias = new ArrayList<ErrorCompilador>();

    private int ultimoTemporal;
    private int ultimaEtiqueta;

    ProgramaIntermedio(List<Cuadruplo> original) {
        codigo = Cuadruplos.copiar(original);
        Cuadruplos.renumerar(codigo);

        for (Simbolo s : TablaSimbolos.obtenerTodos()) {
            tipos.put(s.nombreCodigo, s.tipo);
            if ("CONSTANTE".equals(s.categoria)) {
                constantes.add(s.nombreCodigo);
            }
            if ("ARREGLO".equals(s.categoria) || "MATRIZ".equals(s.categoria)) {
                arreglos.add(s.nombreCodigo);
            }
        }
        for (int i = 0; i < GeneradorCodigo.cantidadDeTemporales(); i++) {
            tipos.put(GeneradorCodigo.nombreDeTemporal(i), GeneradorCodigo.tipoDeTemporal(i));
        }

        ultimoTemporal = GeneradorCodigo.cantidadDeTemporales();
        for (Cuadruplo c : codigo) {
            if (Cuadruplos.esEtiqueta(c) && c.resultado.matches("L\\d+")) {
                ultimaEtiqueta = Math.max(ultimaEtiqueta, Integer.parseInt(c.resultado.substring(1)));
            }
        }
    }

    /** Temporal nuevo, a continuacion del ultimo que repartio el generador. */
    String nuevoTemporal(String tipo) {
        ultimoTemporal++;
        String t = "t" + ultimoTemporal;
        tipos.put(t, tipo);
        return t;
    }

    /** Etiqueta nueva, a continuacion de la mayor que ya existe. */
    String nuevaEtiqueta() {
        ultimaEtiqueta++;
        return "L" + ultimaEtiqueta;
    }

    /** Tipo de un nombre, o null si no se conoce. */
    String tipoDe(String nombre) {
        return tipos.get(nombre);
    }

    /** Crea un cuadruplo con su forma legible ya escrita. */
    static Cuadruplo nuevo(String op, String a1, String a2, String res) {
        return new Cuadruplo(-1, op, a1, a2, res, GeneradorCodigo.legible(op, a1, a2, res));
    }

    /** Rehace la forma legible despues de cambiar alguna casilla. */
    static void reescribir(Cuadruplo c) {
        c.comentario = GeneradorCodigo.legible(c.operador, c.arg1, c.arg2, c.resultado);
    }
}
