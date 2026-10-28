// Archivo: CostosEjecucion.java
//
// Costo relativo de ejecutar cada operador del codigo intermedio. Con esta tabla
// y las veces que se ejecuta cada instruccion (lo cuenta el simulador) se
// obtiene el costo dinamico de un programa, que es la medida con la que se
// compara el codigo antes y despues de optimizar.
//
// Los valores son unidades relativas, no ciclos de una maquina concreta. Siguen
// el orden habitual: copiar o sumar es lo mas barato; multiplicar cuesta mas;
// dividir y elevar mucho mas; tocar un arreglo cuesta un acceso extra a memoria;
// un salto condicional compara y salta; la entrada/salida es lo mas caro.
//
// Se pueden cambiar sin recompilar con un archivo .properties (operador=costo),
// por ejemplo:   *=4   si_=3   (la clave "si_" vale para todos los si_<relop>).
package backend.optimizacion;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class CostosEjecucion {

    private final Map<String, Integer> costos = new LinkedHashMap<String, Integer>();

    /** La tabla por omision. */
    public CostosEjecucion() {
        poner(1, "=", "+", "-", "-u", "!!", "&&", "||", ">", "<", ">=", "<=", "==", "!=", "ir_a");
        poner(2, "ampliar", "=[]", "[]=", "si_");
        poner(3, "*");
        poner(5, "a_texto", "concat");
        poner(10, "/", "%", "imprimir", "leer");
        poner(20, "**");
    }

    /** La tabla por omision, con los valores del archivo encima. */
    public static CostosEjecucion desdeArchivo(String ruta) throws IOException {
        CostosEjecucion c = new CostosEjecucion();
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(ruta)) {
            p.load(in);
        }
        for (String clave : p.stringPropertyNames()) {
            c.costos.put(clave.trim(), Integer.valueOf(p.getProperty(clave).trim()));
        }
        return c;
    }

    /** Costo de una ejecucion del operador. Las etiquetas no cuestan nada. */
    public int costo(String operador) {
        if ("etiqueta".equals(operador)) {
            return 0;
        }
        String clave = (operador.startsWith("si_") && !costos.containsKey(operador)) ? "si_" : operador;
        Integer v = costos.get(clave);
        return (v == null) ? 1 : v.intValue();
    }

    public Map<String, Integer> tabla() {
        return costos;
    }

    private void poner(int costo, String... operadores) {
        for (String op : operadores) {
            costos.put(op, Integer.valueOf(costo));
        }
    }
}
