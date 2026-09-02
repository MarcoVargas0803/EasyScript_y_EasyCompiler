# Plan de correcciones — EasyCompiler

Bitácora de trabajo para ir corrigiendo el compilador poco a poco. Cada punto es
independiente y se puede atacar por separado.

**Alcance actual del proyecto:** analizador léxico + sintáctico. Todavía no existen
la fase semántica ni las siguientes, así que todo lo que aparece aquí se limita a
esas dos fases o a preparar el terreno para las que siguen.

**Regla de oro para cualquier cambio:** antes de tocar la gramática, capturar la
salida actual de los 19 archivos de prueba; después del cambio, volver a correrlos y
comparar. La recuperación de errores es la parte más valiosa del proyecto y no debe
degradarse. Ver la sección *Cómo verificar* al final.

---

## 1. Árbol sintáctico con JJTree — ✅ HECHO

**Problema:** las 22 producciones devolvían `void`. El árbol sintáctico solo existía
como árbol de derivación implícito en la pila de llamadas del parser y se destruía
conforme cada método retornaba. No se podía guardar, imprimir ni recorrer.

**Solución aplicada:**

- `EasyCompiler.jj` → **`EasyCompiler.jjt`**. JJTree (incluido en el mismo
  `javacc.jar`) inserta automáticamente la construcción del árbol.
- Opciones nuevas: `MULTI=false`, `NODE_DEFAULT_VOID=false`, `TRACK_TOKENS=true`,
  `VISITOR=false`. Con eso se crea **un nodo por cada producción sin anotar ni una
  sola regla de la gramática**.
- `Programa()` pasó de `void` a `SimpleNode` y devuelve `jjtThis` (la raíz).
- Nueva bandera `EasyCompiler.arbolParcial`, que se enciende en el `catch` de la
  regla raíz para avisar que el árbol quedó incompleto.
- **`ImpresorArbol.java`** (nuevo): recorre el árbol y lo dibuja. Los tokens se
  muestran como hojas sin haber anotado la gramática, aprovechando que con
  `TRACK_TOKENS` cada nodo conoce su rango `[primerToken .. últimoToken]` y los
  rangos de los hijos son contiguos: todo token del padre que no cae dentro de un
  hijo es una hoja de ese padre.
- `Main.java` imprime el árbol y acepta `--arbol-completo`.
- El colapso de los niveles de precedencia se hace **al imprimir**, no con
  descriptores `#Nodo(>1)` de JJTree, precisamente para que la bandera pueda
  alternar entre los dos modos con un solo parser.

**Nota:** hubo que cambiar `new ArrayList<>()` por `new ArrayList<ErrorCompilador>()`
porque el parser de Java que trae JJTree es más viejo que el de JavaCC y no acepta el
operador diamante.

---

## 2. Un símbolo inválido tumba el resto del archivo

**Problema.** `ENT A = 5 $ 3;` produce: error léxico por el `$` (bien), luego se
inserta el `;` virtual, y luego el `3` suelto no puede iniciar ninguna sentencia →
el error escala hasta el `catch` de `Programa()`, que consume hasta EOF. **Un solo
carácter mal tecleado cuesta el análisis sintáctico de todo el resto del archivo.**

**Causa.** `Sentencias()` (`EasyCompiler.jjt`) no tiene `try/catch` propio, así que
un token que no inicia ninguna sentencia no tiene dónde ser atrapado más que en la
raíz.

**Propuesta.** Envolver el cuerpo de `Sentencias()` en su propio `try/catch` con
`recuperarHastaSync(SYNC_SENTENCIAS)`, para acotar el daño a una sola sentencia.

**Cuidado:** es el cambio con más riesgo de toda la lista, porque `Sentencias()` es
recursiva (se llama desde `SI`, `PARA`, `MIENTRAS`, `SEGUN` y `BloqueAnonimo`). Hay
que verificar que no se rompa el balanceo de llaves.

---

## 3. Precedencia de `**` y del menos unario

Ambos casos se aceptan hoy sin error, así que la agrupación ya quedó decidida por la
forma del árbol:

| Código | Cómo lo agrupa la gramática | Lo convencional |
|---|---|---|
| `2 ** 3 ** 2` | `(2**3)**2` = 64 — izquierda | derecha → 512 |
| `-2 ** 2` | `(-2)**2` = 4 | `-(2**2)` = −4 |

**Propuesta.** `ExpresionNivel3` debe ser recursiva a la derecha para `**`; el
`( <RESTA> )?` de `ExpresionNivel4` debe subir a un nivel de menor precedencia.

**Prioridad alta ahora que el árbol existe:** en cuanto la fase semántica evalúe el
árbol, estos dos dejan de ser teóricos y producen resultados numéricos incorrectos.

---

## 4. Mensajes de error ilegibles por exceso de alternativas

`obtenerDetalleSintactico()` vuelca **todas** las alternativas esperadas. Un caso
real llega a 34:

> Se encontró '3', pero se esperaba: ENT o DEC o BOOL o LETRA o TXT o ARREGLO o
> MATRIZ o SI o SEGUN o PARA o MIENTRAS o LEER o IMPRIMIR o + o - o * o / o % o **
> o == o != o < o <= o > o >= o && o || o { o } o [ o ; o …

**Propuesta.** Truncar a las primeras ~5 y agregar `…`.

**Bug adicional en la misma función:** el separador `" o "` se agrega comparando
`i < length - 1` **fuera** del bucle interno, así que los tokens de una misma
secuencia esperada se concatenan sin separador. Se nota poco porque casi todas las
secuencias son de longitud 1, pero está mal.

---

## 5. Posición reportada del `;` faltante

`PuntoYComaVirtual()` reporta en `getToken(1)`, o sea **el token siguiente**. Un `;`
que falta en la línea 2 se reporta en la línea 3.

**Propuesta.** Reportar en el final del token anterior: `getToken(0).endLine` /
`endColumn`.

---

## 6. `COMENTARIO_LINEA` exige salto de línea final

La expresión regular termina en `("\n"|"\r"|"\r\n")`, obligatorio. Un `//` en la
última línea del archivo sin salto de línea no casa, y cada carácter cae en
`ERROR_SIMBOLO` provocando una avalancha de errores léxicos.

**Propuesta.** Hacer opcional el terminador: `("\n"|"\r"|"\r\n")?`.

---

## 7. `ExpresionConcatenada()` es código inalcanzable

Define `ExpresionAritmetica() ( <SUMA> ExpresionAritmetica() )*` para la
concatenación de `IMPRIMIR`, pero `ExpresionNivel1` ya consume **todos** los `+` de
forma voraz antes de retornar. Verificado en el parser generado: el `case SUMA` de
`ExpresionConcatenada` nunca se alcanza.

**Consecuencia.** En `IMPRIMIR("m" + A)` el `+` es un nodo de **suma aritmética**, no
de concatenación. La fase semántica tendrá que distinguir suma de concatenación **por
tipos, no por forma del árbol**.

**Propuesta.** Decidir entre rediseñar la regla o documentar la decisión. Está
relacionado con el punto 3.

---

## 8. Charset explícito al leer el archivo fuente

`Main` abre un `FileInputStream` crudo, así que `SimpleCharStream` decodifica con el
charset por defecto de la JVM. Los archivos de prueba usan acentos dentro de
identificadores válidos (`Variable_con_guión`) y en cadenas (`"Juánito"`). En un
equipo con otro `file.encoding` esos identificadores se leen distinto.

**Propuesta.** Construir el parser con `new EasyCompiler(archivo, "UTF-8")` o pasar
un `InputStreamReader` con UTF-8 explícito.

---

## 9. `listaErrores` es `static`

Funciona porque `Main` corre una sola vez, pero si se agrega interfaz gráfica y se
compila dos veces, los errores se acumulan entre corridas.

**Propuesta.** Volverla de instancia, o limpiarla al inicio de cada análisis. Aplica
igual a la nueva bandera `arbolParcial`.

---

## 10. Documentar la dependencia del orden de declaración de tokens

JavaCC resuelve: (1) gana la coincidencia más larga; (2) en empate, gana el token
**declarado primero**. Todo el diseño de tokens de error depende de eso:

| Entrada | Compiten | Gana | Por qué |
|---|---|---|---|
| `'a'` | `VALOR_LETRA` (3) vs `ERROR_LONGITUD_CARACTER` (3) | `VALOR_LETRA` | empate → declarado antes |
| `INICIO` | `INICIO_PROGRAMA` (6) vs `ERROR_IDENTIFICADOR_INVALIDO` (6) | keyword | empate → declarado antes |
| `CONST_Pi` | `CONSTANTE` (8) vs `ERROR_IDENTIFICADOR_INVALIDO` (8) | `CONSTANTE` | empate → declarado antes |

**Si alguien mueve el bloque de tokens de error arriba de las palabras reservadas,
`INICIO`, `VERDADERO`, `IMPRIMIR` y toda constante se vuelven errores léxicos y el
lenguaje deja de existir.**

**Propuesta.** Comentario de advertencia grande en la gramática, justo antes del
bloque de errores léxicos.

---

## 11. Alinear el README con la gramática real

- `PARA` **exige** tipo de dato: `PARA (I = 0; ...)` falla. Y el incremento debe ser
  `Variable++` / `Variable--`; `I = I + 1` falla.
- `SEGUN` solo acepta un `<VARIABLE>`, ni constante ni expresión.
- `!!` está documentado como operador lógico binario junto a `&&` y `||`, pero en la
  gramática es un **prefijo unario** de `CondicionSimple`.
- `CASO` acepta cualquier `Valor()`, incluidas cadenas y variables — más permisivo de
  lo documentado.

---

## 12. Limpieza menor y deuda documentada

- **Ramas muertas en `Main.java`.** `Programa()` atrapa toda `ParseException`, así
  que el `catch (ParseException)` de `Main` es inalcanzable. Y como el `SKIP` de
  `ERROR_SIMBOLO` es `~[]` y el estado `IN_COMMENT` también, no queda carácter que el
  lexer no pueda consumir, por lo que `TokenMgrError` tampoco se alcanza. Conviene
  conservarlas como red de seguridad, pero documentadas como tales.
- **`Token err;` sin inicializar** en `Valor()` — inicializar a `null` como ya se hace
  en `CondicionSimple()`.
- **Las 7 advertencias de *choice conflict*** (`+`, `*`, `**`, `[`, `[`, `>`, `&&`)
  tienen todas la misma causa: las ramas vacías de la recuperación por inserción
  contaminan los conjuntos FOLLOW. JavaCC resuelve de forma voraz y la conducta es la
  correcta. **No hay que "arreglarlas" metiendo `LOOKAHEAD`: eso rompería la
  recuperación.** Documentarlas como precio consciente del diseño.

---

## Cómo verificar cualquier cambio

```powershell
# 1. Compilar (jjtree -> javacc -> javac)
.\build.ps1

# 2. Analizar un archivo
.\build.ps1 -Ejecutar codigo1.txt

# 3. Ver la derivacion completa, sin colapsar
.\build.ps1 -Ejecutar codigo1.txt -Completo
```

**Criterio de aceptación al tocar la gramática:**

1. `javacc` debe reportar **0 errores y exactamente 7 advertencias** de *choice
   conflict* (`+`, `*`, `**`, `[`, `[`, `>`, `&&`). Una advertencia nueva significa
   que se alteró la gramática sin querer.
2. La salida de errores de los 19 archivos de prueba debe ser **idéntica** en
   cantidad, tipo, línea, columna, detalle y consejo — salvo que el punto que se está
   corrigiendo la cambie a propósito.

Archivos de prueba y su salida esperada hoy:

| Archivos | Errores |
|---|---|
| `Hello world.txt`, `codigo1.txt`, `codigo2.txt`, `codigo3.txt`, `scratch_test.txt`, `finalprueba.txt`, `codigo-prueba1..3.txt`, `codigoSimulación.txt` | 0 |
| `errorcaracter.txt` | 1 (Léxico 3:24) |
| `erroroplog.txt` | 1 (Léxico 5:7) |
| `testcode.txt` | 1 (Sintáctico 2:28) |
| `Negativos.txt` | 1 (Sintáctico 42:2) |
| `erroridentificador.txt` | 2 (Léxico 3:7 y 4:12) |
| `FORsencillo.txt` | 2 (Léxico 3:11 y 5:22) |
| `sensor.txt` | 5 |
| `errorescomunes.txt`, `codigopruebas.txt` | comparar contra la corrida anterior |
