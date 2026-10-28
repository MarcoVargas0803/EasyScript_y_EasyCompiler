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

Como el valor final de las variables del usuario es observable, se consideran **vivas
al terminar el programa**: solo se elimina una asignación a una de ellas si se pisa
antes de usarse. `LEER` e `IMPRIMIR` nunca se eliminan.

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

Son unidades relativas. Se cambian sin recompilar con
`VerificadorOptimizacion -costos archivo.properties` (`operador=costo`; `si_` vale
para todos los saltos condicionales).

### Banco de pruebas

Además de los 30 programas de `Tests\` (13 se pueden simular; los demás tienen
errores a propósito), `Tests\optimizacion\` tiene un programa por fase. Cada uno trae
en su cabecera la salida esperada, que no debe cambiar al optimizar:

| Programa                | Casos                                                                 |
| ----------------------- | --------------------------------------------------------------------- |
| `opt_constantes`        | cadenas de constantes, `CONST_`, DEC, concat, `1 / (2 - 2)` en `SI (FALSO)` |
| `opt_redundancias`      | subexpresiones repetidas, `V[X]` dos veces, escritura entre lecturas, asignación repetida |
| `opt_reordenacion`      | `A*B` / `B*A`, `A + 1 + 2`, identidades, DEC sin reasociar            |
| `opt_bucles`            | invariantes, recorrido de arreglo y matriz, división en ciclo sin vueltas |
| `opt_regiones`          | SI que comparte etiqueta con un MIENTRAS, constantes entre bloques    |
| `opt_muertas`           | asignaciones muertas en un camino, en todos, y `LEER` sin usar        |

### Punto de partida (F0: sin optimizar)

19 programas optimizables (13 de `Tests\` + 6 del banco):

| Métrica             | Total  |
| ------------------- | ------ |
| Instrucciones       | 894    |
| Instr. ejecutadas   | 21 448 (20 000 son de `codigo2`, que no termina) |
| Costo dinámico      | 72 501 |
| Bytes de temporales | 1 913  |

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

| Métrica (19 programas) | F0     | F1     | Cambio  |
| ---------------------- | ------ | ------ | ------- |
| Instrucciones          | 894    | 751    | −16.0 % |
| Instr. ejecutadas      | 21 448 | 21 304 | −0.7 %  |
| … sin `codigo2`        | 1 448  | 1 304  | −9.9 %  |
| Costo dinámico         | 72 501 | 71 970 | −0.7 %  |
| … sin `codigo2`        | 3 975  | 3 444  | −13.4 % |
| Bytes de temporales    | 1 913  | 1 224  | −36.0 % |

`codigo2` no termina: el simulador lo corta a los 20 000 pasos y su cifra no cambia.
F1 no actúa dentro de los bucles cuando el valor viene de otro bloque (`opt_bucles`,
`opt_regiones` siguen igual): eso le toca a F5.

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

Ejemplos: `(X + Y) * (X + Y)` calcula `X + Y` una vez; `V[X] + V[X]` calcula la
dirección y lee el elemento una vez; `t = Contador + 1 ; Contador = t` queda en
`Contador = Contador + 1`.

| Métrica (19 programas)      | F0     | F1     | F2     | F0→F2   |
| --------------------------- | ------ | ------ | ------ | ------- |
| Instrucciones               | 894    | 751    | 693    | −22.5 % |
| Instr. ejecutadas sin `codigo2` | 1 448 | 1 304 | 1 182 | −18.4 % |
| Costo dinámico sin `codigo2`    | 3 975 | 3 444 | 3 292 | −17.2 % |
| Bytes de temporales         | 1 913  | 1 224  | 988    | −48.4 % |

## Depuración

Toda transformación pasa por `Bitacora.registrar`: un punto de interrupción ahí
muestra en vivo la fase, la regla y la instrucción antes y después. Para aislar la
fase que introduce un fallo: `.\pruebas_opt.ps1 -Comparar -Hasta k`, subiendo `k`
hasta que aparezca.
