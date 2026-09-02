# Resumen de Cambios (Changelog) — EasyCompiler

---

# Actualización: Construcción, Almacenamiento e Impresión del Árbol Sintáctico

Esta actualización cierra el punto 1 de `plan_correcciones.md`. Hasta ahora el compilador **validaba** el código pero no **conservaba** nada: el árbol sintáctico existía únicamente como el árbol de derivación implícito en la pila de llamadas del parser descendente recursivo, y se destruía conforme cada producción retornaba.

## 1. Migración de JavaCC puro a JJTree
* **Archivos modificados:** `EasyCompiler.jj` → `EasyCompiler.jjt`, `.gitignore`, `build.ps1` (nuevo)
* **Cambio:** La gramática pasó a ser un archivo `.jjt` procesado por JJTree, el preprocesador que viene incluido en el mismo `javacc.jar`. La construcción ahora tiene tres pasos: `EasyCompiler.jjt` → (jjtree) → `EasyCompiler.jj` → (javacc) → `*.java`. Se agregaron las opciones `MULTI=false`, `NODE_DEFAULT_VOID=false`, `TRACK_TOKENS=true` y `VISITOR=false`.
* **Justificación:** JJTree inserta automáticamente las acciones de construcción del árbol, de modo que **no fue necesario anotar ni una sola de las 22 producciones** ni programar estructuras de datos a mano. La única modificación de gramática fue que `Programa()` pasara de `void` a `SimpleNode` para devolver la raíz. Esto es además el prerrequisito técnico de toda la fase semántica: sin árbol no hay dónde colgar tipos, ámbitos ni código intermedio.

## 2. Impresión del árbol con los tokens como hojas
* **Archivos modificados:** `ImpresorArbol.java` (nuevo), `Main.java`
* **Cambio:** Se agregó una clase dedicada que recorre el árbol y lo dibuja indentado, mostrando cada token como hoja con su nombre simbólico y su lexema (`<ENTERO_DATO: "ENT">`). El mapa de nombres de token se arma por reflexión sobre `EasyCompilerConstants`, para que se mantenga solo si la gramática cambia.
* **Justificación:** JJTree crea nodos para las producciones pero no para los tokens. En lugar de ensuciar la gramática con ~60 anotaciones `#Terminal`, se aprovecha que con `TRACK_TOKENS` cada nodo conoce su rango `[primerToken .. últimoToken]` y que los rangos de los hijos son contiguos y están en orden: todo token del padre que no cae dentro de un hijo es una hoja de ese padre. La gramática queda limpia y toda la lógica de presentación vive en Java normal, fuera del código generado.

## 3. Modo colapsado y bandera `--arbol-completo`
* **Archivos modificados:** `ImpresorArbol.java`, `Main.java`
* **Cambio:** Por defecto se omiten los eslabones vacíos de la cascada de precedencia (`Condicion → CondicionSimple → ExpresionAritmetica → ExpresionNivel1..4 → ValorConArreglos → Valor`). La bandera `--arbol-completo` imprime la derivación literal.
* **Justificación:** Un literal suelto como `5` atraviesa 9 producciones antes de llegar a la hoja, lo que vuelve ilegible el árbol de cualquier programa real. El colapso se hace **al imprimir** y no con descriptores condicionales `#Nodo(>1)` de JJTree, precisamente porque esos quedan fijados en tiempo de compilación y harían imposible alternar entre los dos modos con un solo parser. Un nodo solo se omite si su rango de tokens es idéntico al de su único hijo, de modo que nunca se pierden símbolos propios del padre como el `-` unario o los paréntesis.

## 4. Aviso de árbol parcial
* **Archivos modificados:** `EasyCompiler.jjt`, `Main.java`
* **Cambio:** Nueva bandera `EasyCompiler.arbolParcial`, encendida en el `catch` de la regla raíz, que hace que `Main` advierta cuando el árbol impreso está incompleto.
* **Justificación:** Cuando un error escala hasta `Programa()`, el análisis sintáctico se detiene y consume hasta EOF. Imprimir el árbol truncado sin avisar daría la impresión falsa de que ese es el programa completo.

## 5. Compatibilidad: operador diamante
* **Archivos modificados:** `EasyCompiler.jjt`
* **Cambio:** `new ArrayList<>()` pasó a `new ArrayList<ErrorCompilador>()`.
* **Justificación:** El parser de Java que trae JJTree es más antiguo que el de JavaCC y rechaza el operador diamante `<>`, deteniendo la generación con un error de sintaxis. El tipo explícito es equivalente y funciona en ambos.

## Verificación
Se comprobó que la migración **no degradó la recuperación de errores**, que es la parte más elaborada del proyecto:
* `javacc` sigue reportando **0 errores y exactamente las mismas 7 advertencias** de *choice conflict*.
* La salida de errores de los **19 archivos de prueba es idéntica** a la de antes de la migración, en cantidad, tipo, línea, columna, detalle y consejo.
* Los `try/catch` de recuperación quedan anidados dentro del envoltorio de ámbito de nodo de JJTree, así que las `ParseException` las sigue atrapando el manejador propio y el nodo cierra con los hijos parciales que alcanzó a acumular. Eso hace que la recuperación sea **visible en el árbol**: se ve el `BloqueAnonimo` rescatando sentencias, y un `PuntoYComaVirtual` sin hoja donde se insertó un `;` que no existía en el código fuente.

---

# Actualización anterior: Manejo de Errores

Este documento detalla los cambios estructurales realizados en el compilador **EasyCompiler** durante este commit, enfocados en elevar el estándar del manejo de errores a un nivel profesional (Industrial-grade UX).

---

## 1. Implementación de Estados Léxicos para Fin de Archivo (EOF)
* **Archivos modificados:** `EasyCompiler.jj`
* **Cambio:** Se reemplazó la expresión regular estática de los comentarios de bloque (`/* ... */`) por la gestión mediante estados léxicos (`<IN_COMMENT>`).
* **Justificación:** Anteriormente, si el código fuente contenía un comentario de bloque que nunca se cerraba y se alcanzaba el final del archivo (`EOF`), el analizador léxico de JavaCC lanzaba una excepción fatal incontrolable que rompía la ejecución. Ahora, el lexer cambia a un estado dedicado, detecta el `<EOF>` de manera segura, inserta un `ErrorCompilador` amigable en la lista y retorna al flujo normal, garantizando que el compilador nunca colapse.

## 2. Recuperación por Inserción (Insertion Recovery)
* **Archivos modificados:** `EasyCompiler.jj`
* **Cambio:** Se eliminaron las sentencias de *Panic Mode* para símbolos terminales clave y se reemplazaron por inserciones virtuales.
    * **Puntos y comas (`;`):** Se creó la regla `PuntoYComaVirtual()`.
    * **Paréntesis y corchetes (`)`, `]`):** Se agregaron condicionales de error dentro de `ValorConArreglos()`.
* **Justificación:** El antiguo modelo estricto provocaba "avalanchas de errores" o enmascaraba el verdadero error. Si faltaba un paréntesis en `(5 + 2;`, el error escalaba destruyendo el análisis lógico. Con *Insertion Recovery*, el compilador "finge" que el programador sí escribió el símbolo faltante, registra exactamente dónde faltó y continúa compilando el resto del archivo sin perder contexto.

## 3. Sobrescritura y Captura de `TokenMgrError`
* **Archivos modificados:** `Main.java`
* **Cambio:** Se refactorizó la clase `Main` para atrapar la excepción `TokenMgrError` dentro del flujo lógico principal.
* **Justificación:** Los errores léxicos profundos (como caracteres ASCII corruptos que burlen la regla `~[]`) provocaban que el programa abortara imprimiendo un mensaje de consola feo y evadiendo el renderizado de la interfaz visual. Al atraparlo, el error se convierte en una tarjeta de error estandarizada `[ Error X ]`, lo cual mantiene intacta la Experiencia de Usuario (UX) bajo cualquier circunstancia catastrófica.

## 4. Refinamiento de Consejos Dinámicos (Hints)
* **Archivos modificados:** `EasyCompiler.jj`
* **Cambio:** Se actualizaron y enriquecieron los mensajes de retroalimentación (Consejos) en los bloques `catch` para ser más explícitos y precisos. (Ej: Aclarar que las variables pueden contener guiones bajos, y explicar por qué un paréntesis fue auto-insertado).
* **Justificación:** Un compilador moderno no solo debe encontrar errores, sino educar al desarrollador. Mejores mensajes reducen drásticamente el tiempo de depuración del programador que utiliza **EasyScript**.
