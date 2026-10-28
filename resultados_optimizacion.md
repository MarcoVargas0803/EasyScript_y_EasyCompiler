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
| `opt_redundancias`      | subexpresiones repetidas, `V[X]` dos veces, escritura entre lecturas  |
| `opt_reordenacion`      | `A*B` / `B*A`, `A + 1 + 2`, identidades, DEC sin reasociar            |
| `opt_bucles`            | invariantes, recorrido de arreglo y matriz, división en ciclo sin vueltas |
| `opt_regiones`          | SI que comparte etiqueta con un MIENTRAS, constantes entre bloques    |
| `opt_muertas`           | asignaciones muertas en un camino, en todos, y `LEER` sin usar        |

### Punto de partida (F0: sin optimizar)

19 programas optimizables (13 de `Tests\` + 6 del banco):

| Métrica             | Total  |
| ------------------- | ------ |
| Instrucciones       | 888    |
| Instr. ejecutadas   | 21 442 (20 000 son de `codigo2`, que no termina) |
| Costo dinámico      | 72 478 |
| Bytes de temporales | 1 905  |

### Depuración

Toda transformación pasa por `Bitacora.registrar`: un punto de interrupción ahí
muestra en vivo la fase, la regla y la instrucción antes y después. Para aislar la
fase que introduce un fallo: `.\pruebas_opt.ps1 -Comparar -Hasta k`, subiendo `k`
hasta que aparezca.
