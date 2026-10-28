# Optimización del código intermedio — resultados

El optimizador trabaja sobre una **copia** de los cuádruplos que deja el esquema de
traducción y vive en el paquete `backend.optimizacion`. Corre siempre que el programa
no tenga errores; con errores el código intermedio es parcial y no se optimiza.

```powershell
easy Tests\optimizacion\opt_bucles.easy --codigo-optimizado        # código y métricas
easy Tests\optimizacion\opt_bucles.easy --bitacora-optimizacion    # cada transformación
.\pruebas_opt.ps1 -Comparar                                        # arnés de regresión
.\pruebas_opt.ps1 -Comparar -Hasta 3                               # solo F1-F3
```

## Fase 0 — Banco de pruebas y métricas

### Cómo se comprueba que una optimización es correcta

`VerificadorOptimizacion` ejecuta en el simulador el código original y las versiones
optimizadas con F1, F1–F2, …, F1–F6. **Las trazas deben ser idénticas**: mismas
salidas, mismas lecturas y mismo valor final de cada variable del usuario. Si el
simulador corta un programa que no termina (`codigo2.easy`), basta con que una salida
sea prefijo de la otra. Una sola traza distinta hace fallar el arnés.

El valor final de las variables del usuario es observable: se consideran **vivas al
terminar el programa**.

### Métricas

| Grupo     | Métrica                 | Qué mide                                                       |
| --------- | ----------------------- | -------------------------------------------------------------- |
| Estáticas | Instrucciones           | cuádruplos sin contar etiquetas                                |
|           | Etiquetas, saltos       | forma del control de flujo                                     |
|           | Temporales, bytes       | valores intermedios distintos y la memoria que ocupan          |
|           | Marco                   | datos + temporales: toda la pila del programa (no hay funciones) |
|           | Costo estático          | suma del costo de cada instrucción escrita                     |
| Registros | Máx. temporales vivos   | temporales que tendrían que estar en registros a la vez        |
|           | Mínimo de auxiliares    | cota del algoritmo del número mínimo de variables auxiliares   |
| Dinámicas | Instr. ejecutadas       | pasos del simulador                                            |
|           | Costo dinámico          | Σ ejecuciones(op) · costo(op)                                  |
|           | Lecturas / escrituras   | accesos a memoria de variables, temporales y elementos         |

Las dinámicas usan la entrada fija del simulador: cada `LEER` recibe 3, 5, 1, 0, 2, 4.

### Tabla de costos (`CostosEjecucion`)

| Costo | Operadores                                                      |
| ----- | --------------------------------------------------------------- |
| 1     | `=` `+` `-` `-u` `!!` `&&` `\|\|` relacionales `ir_a`           |
| 2     | `ampliar` `=[]` `[]=` saltos condicionales (`si_…`)             |
| 3     | `*`                                                             |
| 5     | `a_texto` `concat`                                              |
| 10    | `/` `%` `imprimir` `leer`                                       |
| 20    | `**`                                                            |

Unidades relativas; se cambian con `VerificadorOptimizacion -costos archivo.properties`.

### Banco de pruebas

Los 13 programas simulables de `Tests\` y uno por fase en `Tests\optimizacion\`, con
su salida esperada en la cabecera:

| Programa                | Casos                                                                 |
| ----------------------- | --------------------------------------------------------------------- |
| `opt_constantes`        | cadenas de constantes, `CONST_`, DEC, concat, `1 / (2 - 2)` en `SI (FALSO)` |
| `opt_redundancias`      | subexpresiones repetidas, `V[X]` dos veces, escritura entre lecturas, asignación repetida |
| `opt_reordenacion`      | `A*B` / `B*A`, `A + 1 + 2`, identidades, DEC sin reasociar, orden de evaluación |
| `opt_bucles`            | invariantes, recorrido de arreglo y matriz, división en ciclo sin vueltas |
| `opt_regiones`          | SI que comparte etiqueta con un MIENTRAS, constantes entre bloques    |
| `opt_muertas`           | asignaciones muertas en un camino, en todos, y `LEER` sin usar        |

## Fase 1 — Ejecución en tiempo de compilación (`PlegadoConstantes`)

Recorre cada bloque básico con la tabla T de pares (nombre, valor), que se vacía al
empezar el bloque, y aplica a cada cuádruplo la primera fila que cumpla de la
Tabla 7.1, repitiendo hasta que nada cambie:

| Fila | Condición                               | Tratamiento                                              |
| ---- | --------------------------------------- | -------------------------------------------------------- |
| 1-2  | un operando está en T                   | se sustituye por su valor                                |
| 3    | todos los operandos son constantes      | se calcula; (res, valor) entra en T y la cuádrupla se elimina |
|      | … y el cálculo falla (división entre 0) | advertencia `OPT-01` y la cuádrupla se deja              |
| 4    | `res = v`                               | sale (res, ·) de T; entra (res, v) si v es constante     |

Decisiones propias de este código intermedio:
- Un temporal plegado solo se elimina si todos sus usos están en el mismo bloque; si
  no, queda como copia `t = valor` (otro bloque lo necesita escrito).
- Las `CONST_` tienen una sola asignación: si es constante, se sustituyen en
  cualquier bloque.
- Un salto condicional con operandos constantes se decide al compilar: pasa a `ir_a`
  o desaparece.
- El valor se calcula con `SimuladorCuadruplos.evaluar`, la misma función que ejecuta
  el código, y solo se pliega si el literal resultante se vuelve a leer igual (un DEC
  como `1.0E10` no se pliega).

Ejemplo (`opt_constantes`, caso a): las 13 instrucciones de
`A = 2 + 3; B = A * 4; C = B - CONST_Base; IMPRIMIR("a: " + A + " " + B + " " + C)`
quedan en `A = 5`, `B = 20`, `C = 10`, `imprimir "a: 5 20 10"`.

## Fase 2 — Eliminación de redundancias (`EliminacionRedundancias`)

Algoritmo de dependencias, dentro de cada bloque básico: todo nombre empieza con
dependencia −1; `dep(i) = 1 + máx(dep de sus operandos)`; si i asigna un nombre,
`dep(nombre) = i`. La cuádrupla i es equivalente a una j anterior si tienen el mismo
operador, los mismos operandos y `dep(i) = dep(j)` (en una asignación, también el
mismo resultado). Entonces i pasa a `(COMO, j)`, su resultado se sustituye por el de
j en adelante, y las `COMO` se borran al final. Que las dependencias coincidan
garantiza que ningún operando cambió entre j e i.

Para este código intermedio:
- Un arreglo es un nombre más: `V[k] = x` actualiza `dep(V)`, así que una lectura de
  `V` después de una escritura nunca se confunde con una anterior (`opt_redundancias`, c).
- `LEER` e `IMPRIMIR` nunca son equivalentes a nada.
- La sustitución solo se hace entre temporales con una única asignación; si no, i se
  cambia por la copia `res_i = res_j`.
- Mirilla de copias: `t = a op b ; X = t`, con t usado solo ahí, pasa a `X = a op b`.

Ejemplo: `(X + Y) * (X + Y)` calcula `X + Y` una vez; `V[X] + V[X]` lee el elemento una vez.

## Fase 3 — Reordenación de operaciones (`ReordenacionOperaciones`)

- **Orden canónico** en `+ * == != && ||`: temporales, arreglos, variables (ambos
  alfabéticos), constantes. `B * A` pasa a `A * B` y F2 lo reconoce. Los relacionales
  se ordenan invirtiendo el operador (`I >= C` es `C <= I`).
- **Identidades**: `x+0`, `x-0`, `x*1`, `x/1` → `x`; `x*0` → `0` (solo ENT). Reducción
  de fuerza local: `x*2` → `x+x`, `x**2` → `x*x`. En DEC solo las exactas en coma
  flotante (`x + 0.0` no lo es: `-0.0 + 0.0` da `0.0`).
- **Reasociación** de constantes enteras: `(A + 1) + 2` → `A + 3`. En DEC no.
- **Orden de evaluación** con el número mínimo de variables auxiliares (hojas 0; hijos
  iguales j → j+1; si no, el máximo): si la rama derecha necesita más, se calcula
  primero. En `opt_reordenacion` (f) los temporales vivos bajan de 3 a 2, el mínimo.
- **Propagación de copias**: `t = x` se sustituye por `x` en su bloque si `x` no cambia.

## Fase 4 — Optimización de bucles (`OptimizacionBucles`)

Sobre los bucles naturales del grafo (arista de vuelta a una cabecera que la domina),
del más interno al más externo. Cada bucle recibe un **bloque de iniciación** justo
antes de su cabecera; los saltos que llegaban a la cabecera desde fuera se redirigen
a él (caso real: el final de un `SI` comparte etiqueta con el `MIENTRAS` que sigue).

- **Código invariante**: un cálculo sale del bucle si cada operando es constante, no
  se asigna dentro (A[v] = 0) o es otro invariante, y su temporal no se usa fuera.
  Una división cuyo divisor no es una constante distinta de 0 no sale: si el bucle
  no da vueltas, el original no la ejecuta (`opt_bucles`, d).
- **Reducción de fuerza** (inicialización / incremento / cuerpo): para la variable de
  inducción `I = I + c` y un derivado `t = a·I + b`, se crea `s = a·I + b` en la
  iniciación, `s = s + a·c` tras el incremento, y `t = I * a` pasa a `t = s`. Cubre
  arreglos (`I * 4`) y matrices (`(F*3 + C) * 4` → `+4` por vuelta de `C`, `+12`
  por vuelta de `F`).

## Fase 5 — Optimización de regiones (`OptimizacionRegiones`)

Lista de regiones: los bucles del más interno al más externo y al final el programa
entero; de cada una se calculan A y R (unión de sus bloques) y B (recalculado por
caminos), visibles en la bitácora. F1-F3 ya trabajan por bloque y F4 llena el bloque
de iniciación; F5 añade lo que solo se ve entre bloques:

- **Constantes entre bloques**: un nombre vale una constante a la entrada de un
  bloque si vale esa misma constante a la salida de todos sus predecesores.
- **Bloques inalcanzables** (los que dejan los saltos decididos al compilar).
- **Mirilla de saltos**: `ir_a` a la siguiente, salto a un salto, salto sobre un
  salto y etiquetas sin uso.

Al llevar constantes entre bloques, F1 puede descubrir divisiones entre cero nuevas:
`opt_bucles` (d) avisa `OPT-01` en un ciclo que nunca se ejecuta, como en el libro.

## Fase 6 — Asignaciones muertas (`AsignacionesMuertas`)

1) En el bloque: otra asignación a v sin uso en medio → muerta; un uso → viva.
2) Si no se decide, por caminos: B[v] = 1 → viva; B[v] = 0 y A[v] = 1 → camino
abandonado; caminos agotados → muerta. Las variables del usuario están vivas al final
del programa. Nunca se quitan `LEER`, `IMPRIMIR`, escrituras en arreglos ni una
división que podría fallar.

Al cerrar, **compactación de temporales**: coloreado del grafo de interferencias (dos
temporales interfieren si uno se asigna mientras el otro está vivo); los del mismo
tipo que no interfieren comparten nombre y dirección.

## Resumen por fase

19 programas optimizables. Se excluye `codigo2`, que no termina: el simulador lo
corta a los 20 000 pasos. Las cifras son acumuladas (F2 incluye F1, etc.).

| Métrica                 | F0    | F1    | F2    | F3    | F4    | F5    | F6    | F0→F6   |
| ----------------------- | ----- | ----- | ----- | ----- | ----- | ----- | ----- | ------- |
| Instrucciones           | 903   | 760   | 701   | 699   | 706   | 564   | 545   | −39.6 % |
| Instr. ejecutadas       | 1 451 | 1 307 | 1 185 | 1 183 | 1 178 | 1 047 | 1 021 | −29.6 % |
| Costo dinámico          | 3 995 | 3 464 | 3 312 | 3 267 | 3 201 | 2 867 | 2 839 | −28.9 % |
| Bytes de temporales     | 1 941 | 1 252 | 1 012 | 1 004 | 1 012 | 784   | 152   | −92.2 % |
| Σ máx. temporales vivos | 31    | 26    | 28    | 27    | 30    | 26    | 25    | −19.4 % |

F2 sube los temporales vivos: reutilizar un valor alarga su vida (el precio clásico
de eliminar subexpresiones comunes). F3 recupera una parte con el orden de evaluación.
F4 cambia multiplicaciones por sumas (menos costo) a cambio de variables que viven
todo el bucle: más instrucciones escritas y más temporales vivos, menos costo dinámico.

## Depuración

Toda transformación pasa por `Bitacora.registrar` (punto de interrupción para verla en
vivo). Para aislar la fase que introduce un fallo: `.\pruebas_opt.ps1 -Comparar -Hasta k`.
