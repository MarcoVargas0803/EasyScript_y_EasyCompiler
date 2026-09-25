# Resumen de Cambios (Changelog) — EasyCompiler

---
# Actualización: Backpatching y corrección de precedencia

Dos correcciones que había que hacer **antes** de seguir generando código, porque una producía código correcto pero ineficiente y la otra producía código sencillamente equivocado.

## 1. La precedencia de `**` y del menos unario estaba invertida

Era el punto 3 de `plan_correcciones.md`, y dejó de ser teórico en cuanto empezó a generarse código:

| Código | Antes | Ahora |
|---|---|---|
| `2 ** 3 ** 2` | `(2**3)**2` = 64 | `2**(3**2)` = 512 |
| `-2 ** 2` | `(-2)**2` = 4 | `-(2**2)` = −4 |

* **Archivos:** `EasyCompiler.jjt`, `AnalizadorSemantico.java`
* **Cambio:** el problema era de estructura, no de código. `ExpresionNivel4` (el menos unario) estaba **debajo** de `ExpresionNivel3` (el exponente), así que el signo se pegaba a la base en lugar de aplicarse al resultado. Se intercambiaron: ahora `ExpresionNivel3` es el unario y `ExpresionNivel4` el exponente. Además el exponente pasó de `( "**" Nivel4 )*` a `( "**" Nivel3 )?`, es decir, de un bucle —que agrupa por la izquierda— a recursión por la derecha.
* **Por qué el operando derecho vuelve a `Nivel3` y no a `Nivel4`:** para que `2 ** -3` siga siendo válido. La base ya no admite signo, pero el exponente sí.
* **Advertencias de *choice conflict*:** siguen siendo **7**. La del `**` cambió de construcción `(...)*` a `[...]` al volverse recursiva, pero no desapareció ninguna ni apareció ninguna nueva.

## 2. `&&` y `||` ahora cortocircuitan, con backpatching

* **Archivos:** `Atributo.java`, `GeneradorCodigo.java`, `AnalizadorSemantico.java`
* **Antes:** `A && B` evaluaba **siempre** las dos partes, las metía en un temporal y luego saltaba. Además de calcular de más, eso hace inseguro escribir algo como `(I < N && Datos[I] > 0)`: la segunda parte se evaluaría con `I` fuera de rango.
* **Ahora:** el operando izquierdo se convierte en saltos **antes** de mirar el derecho, y el código del derecho se emite después del salto, de modo que el salto lo puede esquivar entero.

El problema de hacerlo así es que, al emitir el salto, todavía no se sabe a dónde va: el destino depende de si la condición acaba dentro de un `SI`, de un `MIENTRAS` o guardada en una variable, y eso se descubre más arriba en el árbol. La solución es el **backpatching** (Aho, Compiladores 2a ed., §6.7): el salto se emite con el destino en blanco (`_`), su número de instrucción se apunta en una lista, y la lista se rellena cuando el destino se conoce.

`Atributo` ganó `listaVerdadero` y `listaFalso`; `GeneradorCodigo` ganó `nuevaLista`, `unir`, `completar` y `etiquetaAqui` —este último es el marcador que Aho escribe como `M` en sus reglas—.

Lo que produce `SI (A > 1 && B > 2)`:

```
    t1 = A > 1
    si t1 ir_a L2      <- cierto: hay que seguir mirando
    ir_a L3            <- falso: se sale sin evaluar B
L2: t2 = B > 2
    si t2 ir_a L4
    ir_a L3            <- las dos salidas falsas se fusionaron en L3
L4: imprimir "ambas"
```

## 3. Una condición tiene dos formas, y hay que saber pasar de una a otra

Aho lo trata en §6.6.6: no existe "la" traducción de una condición, sino la que pide el contexto.

* **Como saltos**, cuando dirige un `SI` o un `MIENTRAS`: no se calcula nada.
* **Como valor**, cuando se guarda o se imprime: `BOOL Mayor = Edad > 18 && Tiene_id;` tiene que dejar algo dentro de `Mayor`.

Se añadieron las dos conversiones: `aSaltos()` y `materializar()`. Los sitios que consumen una condición como dato —inicializadores, asignaciones, valores de un arreglo, `IMPRIMIR`, y los paréntesis dentro de una expresión aritmética— materializan; los que la usan como control usan `saltarSiFalso()`, que funciona con las dos formas.

**Decisión de alcance:** las comparaciones sueltas (`A > 3`) siguen produciendo un valor, no saltos. Con la forma pura de Aho, `SI (X > 3)` pasaría de dos instrucciones a tres y `BOOL B = X > 3;` a seis. El cortocircuito —que es lo que de verdad importa— se consigue igual, porque lo decisivo es que el código del operando derecho se emita después del salto. Convertir también las comparaciones es un cambio localizado en `visit(ASTCondicionSimple)` si más adelante se quiere la forma completa.

## Verificación
* **El backpatching se completa siempre.** `GeneradorCodigo.etiquetasRotas()` denuncia cualquier salto cuyo destino no exista, y el marcador `_` nunca es una etiqueta válida: si una lista se quedara sin rellenar, saltaría. Se ejecutó sobre los 26 archivos de prueba: **ninguno tiene saltos sin destino**.
* Los 23 archivos anteriores reportan **exactamente los mismos errores** que antes de estos dos cambios.
* Archivos de prueba nuevos, los dos limpios: `precedencia.txt` (agrupación del exponente y del signo) y `cortocircuito.txt` (`&&`, `||`, encadenado, booleano guardado y condición dentro de un ciclo).
* Casos límite comprobados a mano: `SI (P)` con una variable booleana suelta, `SI (P && Q)` con dos, `SI (!!(P && Q))` y `SI (N > 0 && P || Q)`, donde la lista falsa del `&&` se convierte correctamente en la entrada del operando derecho del `||`.

## Un artefacto conocido
El código generado contiene algún `ir_a L` seguido inmediatamente de `L:`, es decir, un salto a la instrucción siguiente. Sale de que `aSaltos()` emite siempre las dos salidas de una condición, y una de ellas a veces cae justo donde ya estábamos. Es inofensivo, y Aho lo documenta en §6.6.6 al hablar de *evitar gotos redundantes*. Quitarlos es una pasada de mirilla sobre el código ya generado, que no se ha hecho por no salirse del alcance.

---
# Actualización: Esquema de Traducción (código de tres direcciones)

Cierra el último subtema del análisis semántico. El compilador ya no solo comprueba el programa: ahora lo **traduce** a código de tres direcciones, la forma plana e intermedia desde la que se genera código máquina.

Un ejemplo de lo que produce, tomado de `finalprueba.txt`:

```
    An = ((B*N)*(N+1))/2;      ->   t2 = B * N
                                    t3 = N + 1
                                    t4 = t2 * t3
                                    t5 = t4 / 2
                                    An = t5
```

## 1. Qué se generó y dónde vive
* **Archivos:** `Cuadruplo.java` y `GeneradorCodigo.java` (nuevos), `Atributo.java`, `AnalizadorSemantico.java`
* **Cambio:** cada instrucción es un cuádruplo `(operador, arg1, arg2, resultado)` (Aho, Compiladores 2a ed., §6.2.2). `GeneradorCodigo` solo sabe emitir instrucciones y repartir nombres nuevos —temporales `t1, t2…` y etiquetas `L1, L2…`—; no recorre el árbol ni consulta tipos. El recorrido y las decisiones siguen siendo de `AnalizadorSemantico`, que va llamando al generador conforme baja.
* **`Atributo` ganó el campo `lugar`**, que separa dos cosas que antes iban juntas: el **lexema** es lo que el programador escribió (sirve para los mensajes de error) y el **lugar** es dónde vive el valor en el código generado. Para `A + B`, el lexema del resultado es `"A + B"` y su lugar es `t1`.

## 2. Por qué la traducción va en la misma pasada que la comprobación de tipos
Porque necesita justo lo que esa pasada acaba de calcular: el tipo de cada operando, para saber si hay que insertar una conversión, y su lugar. Hacerlo en una tercera pasada obligaría a recalcular los tipos, o a guardarlos en el árbol solo para volverlos a leer. Es además la definición de esquema de traducción de Aho (§2.3.5): la traducción va guiada por el mismo recorrido que analiza.

## 3. Las conversiones dejan de ser implícitas
`DEC Mezcla = A + D;` con `A` de tipo `ENT` genera un cuádruplo `ampliar` explícito antes de la suma. El código intermedio no debe esconder conversiones: quien lo traduzca después no tiene forma de adivinarlas (Aho, §6.5.2).

La suma con un operando `TXT` se emite como operador `concat`, no como `+`. La decisión ya se tomaba por tipos en el cubo semántico; ahora también se ve en el código generado.

## 4. Las estructuras de control se reducen a saltos
Es donde se ve que el código de tres direcciones no tiene bloques ni anidamiento (Aho, §6.6). El recorrido deja de ser "visita a los hijos" y pasa a tener orden propio, porque el código no sale en el mismo orden en que está escrito el programa:

* **`SI` / `CONTRARIO` / `SINO`:** cada rama pide su etiqueta de fallo y todas confluyen en una única etiqueta de salida.
* **`MIENTRAS`:** la etiqueta de entrada va **antes** de la condición, porque hay que reevaluarla en cada vuelta.
* **`PARA`:** el paso se escribe en la cabecera pero se ejecuta al final. La gramática guarda la variable y el operador del paso en el nodo, y el cuádruplo se emite después del cuerpo. Es el único sitio donde el orden del fuente y el del código generado no coinciden.
* **`SEGUN`:** las pruebas se agrupan **al final**, como una tabla de decisión (Aho, §6.8). Puestas delante de cada caso habría que saltar por encima de cada cuerpo y el código tendría el doble de saltos.

## 5. Arreglos y matrices: de posición a byte
`A[i]` se traduce en dos instrucciones —calcular el desplazamiento y leer de ahí— porque el índice puede ser una expresión cualquiera. El desplazamiento se multiplica por el ancho del elemento, ya que las direcciones se cuentan en bytes: el tercer elemento de un arreglo de `DEC` está 24 bytes más adelante, no 3.

Para una matriz se usa la fórmula por filas de Aho (§6.4.3): `(fila * columnas + columna) * ancho`. `Tabla[1][2]` sobre `MATRIZ ENT Tabla[2][3]` genera `t = 1*3`, `t' = t+2`, `t'' = t'*4`.

La lista entre llaves de un inicializador no es una instrucción: se traduce como una escritura indexada por valor, con el desplazamiento ya calculado, porque la posición se conoce al compilar.

## 6. La consola no cambió
El código intermedio **se genera siempre**, pero no se imprime. La salida del compilador sigue teniendo sus tres bloques de siempre: árbol, tabla de símbolos y errores. Para verlo hay una bandera:

```powershell
.\build.ps1 -Ejecutar traduccion.txt -Codigo
```

Por la misma razón, los temporales **no** se instalan en la tabla de símbolos: aparecerían en la tabla impresa y cambiarían el tamaño del marco de datos. Cuando se decida mostrarlos, darles dirección es añadir una llamada a `TablaSimbolos.asignarDireccion()`.

## Verificación
* Los 24 archivos de prueba producen **exactamente los mismos errores** que antes de esta fase, y ninguno imprime código sin pedirlo.
* `GeneradorCodigo.etiquetasRotas()` comprueba que todo salto apunte a una etiqueta que existe. Se ejecutó sobre los 24 archivos: **ninguno tiene saltos rotos**, incluidos los que están llenos de errores semánticos.
* Archivo de prueba nuevo: `traduccion.txt`, un programa correcto que ejercita conversión implícita, `SI`/`CONTRARIO`/`SINO`, `MIENTRAS`, `PARA`, arreglo con carga inicial, matriz, `SEGUN` con `DEFECTO`, `LEER` y concatenación encadenada.

## Lo que queda pendiente
* **Cortocircuito de `&&` y `||`.** Hoy la condición se evalúa entera a un temporal booleano y luego se salta. Evaluar solo lo necesario exige *backpatching* (Aho, §6.7), que es el siguiente paso natural.
* **La precedencia de `**` y del menos unario** (punto 3 de `plan_correcciones.md`). Ya no es teórico: `2 ** 3 ** 2` agrupa por la izquierda y `-2 ** 2` da 4 en vez de −4, y ahora eso sale escrito en los cuádruplos.
* **Optimización.** No hay ninguna: las constantes se pliegan solo para *verificar* (división entre cero, índices fuera de rango), y el cuádruplo se emite igual, para que el código intermedio siga siendo fiel al fuente.

---
# Actualización: Analizador Semántico

El compilador ya no solo comprueba que el código esté bien **formado**: ahora comprueba que tenga **sentido**. Hasta esta actualización, `codigoSI.txt` compilaba sin una sola queja usando dos variables que nunca se declararon.

## 1. El análisis semántico es una pasada aparte sobre el árbol
* **Archivos:** `AnalizadorSemantico.java` (nuevo), `EasyCompiler.jjt`, `Main.java`
* **Cambio:** Se activaron `MULTI = true`, `VISITOR = true` y `NODE_CLASS = "NodoEasy"`. JJTree genera ahora una clase por tipo de nodo (`ASTAsignacion`, `ASTExpresionNivel1`, …), la interfaz `EasyCompilerVisitor` y un `EasyCompilerDefaultVisitor` del que hereda el analizador. `Main` invoca la pasada entre el parseo y la impresión.
* **Justificación:** con `MULTI = false` el visitor generado tendría un único método `visit(SimpleNode, Object)` y el analizador sería un `switch` gigante sobre `getId()`, es decir, nada mejor que la recursión manual que ya hace `ImpresorArbol`. Con `MULTI = true` cada regla semántica vive en un método propio y tipado.

## 2. Los ámbitos se abren y cierran en el visitor, no en la gramática
* **Archivos:** `EasyCompiler.jjt`, `TablaSimbolos.java`, `AnalizadorSemantico.java`
* **Cambio:** `TablaSimbolos.abrirAmbito()`/`cerrarAmbito()` y las tres llamadas a `agregarTipo(...)` salieron de la gramática y pasaron a `visit(ASTSentencias)` y `visit(ASTDeclaracion*)`. Se agregó `padreDeBloque` para conservar la jerarquía de bloques, y `reiniciar()` para limpiar el estado estático.
* **Justificación:** hacerlo durante el parseo dejaba la pila de ámbitos **vacía** al terminar, así que `TablaSimbolos.buscar()` —escrito en la fase anterior y nunca usado— habría devuelto siempre `null` desde una pasada posterior. Moviéndolo al visitor, que recorre el árbol en el mismo orden, la pila vuelve a estar viva justo cuando hay que resolver nombres, y `buscar()` funcionó sin tocarle una línea.
* **Efecto secundario:** se corrigió un falso positivo real. El contador de un `PARA` se declaraba en el ámbito de fuera, de modo que dos ciclos hermanos con `ENT I` se denunciaban como declaración repetida. `codigopruebas.txt` perdió dos errores que nunca debieron existir.

## 3. Decoración del árbol: los operadores dejan de perderse
* **Archivos:** `EasyCompiler.jjt`, `NodoEasy.java` (nuevo), `ImpresorArbol.java`
* **Cambio:** cada producción de expresión anota en su nodo el operador que acaba de leer, y las declaraciones y asignaciones anotan su identificador. `ImpresorArbol` los muestra: `ExpresionNivel2  [*]`, `Asignacion  [=]`, más el tipo inferido entre llaves.
* **Justificación:** JJTree crea un nodo por producción pero **no convierte los operadores en nodos**: el `+` de una suma se consumía como token suelto y desaparecía de la lista de hijos. Sin esa anotación no hay árbol de expresiones de verdad ni con qué comprobar tipos. Son acciones de una línea que solo registran información léxica; no deciden nada.

## 4. Comprobación de tipos con pila semántica y cubo
* **Archivos:** `Tipo.java`, `Atributo.java`, `PilaSemantica.java`, `CuboSemantico.java` (todos nuevos)
* **Cambio:** toda expresión deja exactamente un `Atributo` en la pila; cada operador saca sus operandos, consulta el cubo y apila el resultado. Política de conversiones: `ENT → DEC` implícita, `DEC → ENT` error por pérdida de precisión, y `BOOL`/`LETRA`/`TXT` sin mezclarse con números.
* **Justificación de dos decisiones concretas:**
  * `ENT / ENT` da `ENT` (división entera). Si diera `DEC`, `finalprueba.txt` —que declara `An` como `ENT` y le asigna `((B*N)*(N+1))/2`— dejaría de compilar.
  * La suma con un operando `TXT` concatena, y esa decisión se toma **por los tipos**, no por la forma del árbol. Es la respuesta al punto 7 de `plan_correcciones.md`: `ExpresionConcatenada` es código inalcanzable, porque `ExpresionNivel1` se come todas las sumas antes de que llegue a verlas.
* **Marca de robustez:** la pila toma una marca al entrar en cada sentencia y la restaura al salir, de modo que un subárbol incompleto —de los que deja la recuperación de errores sintácticos— no descuadre el análisis de lo que viene después.

## 5. Catálogo de 37 reglas, con las advertencias aparte de los errores
* **Archivos:** `AnalizadorSemantico.java`, `ErrorCompilador.java`, `Main.java`
* **Cambio:** 33 errores semánticos (`SEM-01` … `SEM-38`) y 4 advertencias (`ADV-01` … `ADV-04`), cada uno con su código estable. `ErrorCompilador` ganó el campo `codigo` con un constructor de 6 argumentos; el de 5 delega, así que ninguna de las ~20 llamadas anteriores se rompió. Las advertencias se cuentan por separado: un programa con advertencias y sin errores dice que compila.
* **Antiavalancha:** el tipo `ERROR` se propaga en silencio (es el `type_error` de Aho, §6.5.2) y un nombre no declarado se instala como `INDEFINIDO` para reportarse una sola vez por ámbito. `TablaSimbolos` sabe que un `INDEFINIDO` no es una declaración y lo sustituye si la declaración de verdad aparece después.
* **Ordenación:** `Main` ordena los errores por línea y columna antes de imprimir. Hacía falta porque los semánticos se detectan en una pasada posterior y, sin ordenar, salían todos al final, detrás de errores de líneas muy anteriores.

## 6. Tabla de direcciones
* **Archivos:** `Simbolo.java`, `TablaSimbolos.java`, `Main.java`
* **Cambio:** tres columnas nuevas en la tabla de símbolos —`ANCHO`, `DESPL` y `DIR`— y el tamaño total del marco de datos. Anchos: `ENT` 4, `DEC` 8, `BOOL` 1, `LETRA` 2, `TXT` 4 (referencia). Sigue la disposición de almacenamiento de Aho (§6.3.4, Fig. 6.17): el desplazamiento se reinicia en cada bloque y un bloque interior arranca donde termina lo que su padre lleva reservado.
* **Detalle que conviene mirar:** dos bloques hermanos arrancan en la misma dirección, porque no están vivos a la vez. En `semantico_ok.txt` los contadores `I` de los dos `PARA` comparten la dirección 64.

## Verificación
* `javacc`: 0 errores y exactamente **7** advertencias de *choice conflict*, sin cambios respecto a antes.
* Se capturó la salida de los 20 archivos de prueba **antes** de empezar y se comparó después de cada fase. **Ningún archivo perdió ni ganó un solo error léxico o sintáctico.**
* Archivos de prueba nuevos: `semantico_ok.txt` (0 errores; sirve para detectar falsos positivos), `semantico_tipos.txt` (20 errores, una casilla del cubo cada uno) y `semantico_errores.txt` (dispara las 37 reglas del catálogo).
* Programas que estaban correctos siguen estándolo: `Hello world.txt`, `codigo1.txt`, `codigo2.txt`, `finalprueba.txt`, `scratch_test.txt`.

## Lo que esta actualización NO incluye
* **El esquema de traducción (subtema 1.5)** queda pendiente por decisión de alcance. La pila semántica se dejó explícita precisamente para que generar código de tres direcciones más adelante no exija rehacer nada.
* **ADV-07** (el paso de un `PARA` que se aleja de su condición) y la comprobación **por filas** del inicializador de una matriz: el árbol aplana `{ {1,2}, {3,4} }` en cuatro valores sueltos, así que solo se comprueba el total de elementos.
* La precedencia de `**` y del menos unario sigue como estaba (punto 3 de `plan_correcciones.md`). No afecta a la comprobación de tipos, pero habrá que corregirla antes de generar código.

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
