# Generación de código intermedio — EasyCompiler

Cubre la Unidad 2 de Lenguajes y Autómatas II (TecNM): **2.1** notaciones, **2.2**
representaciones de código intermedio y **2.3** esquemas de generación. Las citas son de
Aho, Lam, Sethi y Ullman, *Compiladores: principios, técnicas y herramientas*, 2a ed.

```powershell
easy Tests\intermedio_control.easy --codigo-intermedio    # cuádruplos + direcciones de temporales
```

## 1. Decisiones

| Tema | Decisión | Por qué |
|---|---|---|
| Notación | **Postfija** | Los cuádruplos se emiten en postorden: primero los operandos y después el operador, que es como reduce la pila semántica (Aho §2.3.1). La infija solo es la forma legible de cada instrucción. |
| Representación | **Cuádruplos** | Cada instrucción nombra su resultado (`t1`). Si la fase siguiente mueve o borra la instrucción que calcula `t1`, las que usan `t1` no cambian; con triplos habría que renumerar sus referencias (Aho §6.2.3). |
| Temporales | Uno nuevo por operación, **sin reutilizar**, y con dirección | Aho §6.2.1 recomienda un nombre distinto cada vez en el código intermedio; combinarlos es asignación de registros (§8.8), propia del código objeto. |
| Eficiencia | Solo código **bien construido** (cap. 6) | Plegado de constantes, subexpresiones comunes, código muerto y mirilla corresponden a la Unidad 3: aquí ni siquiera se pliega `t = 1 * 4`. |

Las demás notaciones y representaciones se documentan abajo con un mismo ejemplo, pero el
compilador no las emite.

## 2. Un mismo programa en todas las formas (2.1 y 2.2)

```
SI (A > B) {
    X = (A + B) * C;
}
```

**Notaciones (2.1).** Prefija y postfija no necesitan paréntesis: el orden ya fija la agrupación.

| Notación | Asignación | Condición |
|---|---|---|
| Infija | `X = (A + B) * C` | `A > B` |
| Prefija (polaca) | `= X * + A B C` | `> A B` |
| Postfija (polaca inversa) | `X A B + C * =` | `A B >` |

**Notación polaca extendida.** La postfija en un vector, con saltos. `BRF` ("brinca si es
falso") toma la dirección y la condición de la pila; `BRV` brincaría si es verdadera.

```
posición:  0  1  2  3   4    5  6  7  8  9  10  11  12
contenido: A  B  >  12  BRF  X  A  B  +  C  *   =   (sigue el programa)
```

**Código P.** Máquina de pila: nada tiene nombre, todo pasa por la cima.

```
      lod A          ; apila A
      lod B          ; apila B
      grt            ; A > B: deja VERDADERO o FALSO
      fjp L1         ; si es falso, salta a L1
      lda X          ; apila la dirección de X
      lod A
      lod B
      adi            ; A + B
      lod C
      mpi            ; (A + B) * C
      sto            ; guarda la cima en la dirección de X
      lab L1
```

**Triplos** (Aho §6.2.3). Sin campo de resultado: un valor se cita por su número.
**Triplos indirectos:** una lista aparte dice en qué orden se ejecutan; reordenar es mover la
lista, no los triplos.

| # | op | arg1 | arg2 |   | instrucción | triplo |
|---|---|---|---|---|---|---|
| (0) | `<=` | A | B | | 0 | (0) |
| (1) | `si` | (0) | (5) | | 1 | (1) |
| (2) | `+` | A | B | | 2 | (2) |
| (3) | `*` | (2) | C | | 3 | (3) |
| (4) | `=` | X | (3) | | 4 | (4) |

**Cuádruplos** (Aho §6.2.2). Lo que emite EasyCompiler, tal cual:

| # | operador | arg1 | arg2 | resultado | se lee |
|---|---|---|---|---|---|
| 4 | `si_<=` | A | B | L1 | `si A <= B ir_a L1` |
| 5 | `+` | A | B | t1 | `t1 = A + B` |
| 6 | `*` | t1 | C | t2 | `t2 = t1 * C` |
| 7 | `=` | t2 | - | X | `X = t2` |
| 8 | `etiqueta` | - | - | L1 | `L1:` |

| | Polaca | Código P | Triplos | Triplos ind. | Cuádruplos |
|---|---|---|---|---|---|
| ¿Nombra los resultados? | no (pila) | no (pila) | por posición | por posición | sí: `t1`, `t2` |
| Mover una instrucción | rehacer el vector | rehacer | renumerar referencias | solo la lista | nada más |
| Tamaño | mínimo | mínimo | 3 campos | 3 campos + lista | 4 campos |

> **Fuentes.** El código P (máquina Pascal-P) y los saltos `BRF`/`BRV` de la polaca
> extendida **no vienen en Aho**: son de la bibliografía en español del temario y de la
> descripción del código P en Louden, *Construcción de compiladores*, cap. 8.

## 3. Esquemas de generación (2.3)

**2.3.1 Variables y constantes.** Declarar no emite nada: solo reserva espacio (ancho,
desplazamiento y dirección en la tabla de símbolos, Aho §6.3.4). Con valor inicial se emite
una asignación. Un literal es su propio lugar (`5`, `"hola"`). Una variable que oculta a otra
del mismo nombre se llama `X$2` en el código, para que la de dentro no pise a la de fuera; la
tabla impresa no cambia. Los temporales no entran en la tabla de símbolos: reciben dirección
justo después de los datos, como en el registro de activación de Aho (figura 7.5), y se
muestran con `-Codigo`.

**2.3.2 Expresiones.** Un temporal por operador, en postorden. Las conversiones van
**explícitas** (Aho §6.5.2): `ampliar` (ENT→DEC) cuando el resultado es DEC o se compara ENT
con DEC (`**` da siempre DEC), y `a_texto` antes de cada `concat`.

```
DEC D = N + 1.5;            t1 = (DEC) N         IMPRIMIR("N vale " + N);    t8 = (TXT) N
                            t2 = t1 + 1.5                                    t9 = "N vale " . t8
                            D = t2                                           imprimir t9
```

**2.3.3 Asignación.** `X = e` copia el lugar de `e`, ampliado antes si X es DEC y `e` ENT.
`X++` es `X = X + 1` (`1.0` si X es DEC). En `LEER("Edad: " + N)` el `+` no concatena: separa
lo que se muestra de donde se guarda, y se traduce `imprimir "Edad: "` y `leer N`.

**2.3.4 Instrucciones de control.** Código de paso (Aho §6.6.5) con backpatching (§6.7.2).
Quien consume una condición le hereda un *modo*: el cuerpo de un `SI` va justo después, así
que la condición solo salta si es **falsa**, y la comparación y el salto van en una sola
instrucción con el relacional complementario. En un salto, `!!` no emite nada: intercambia
las listas.

```
SI (A > 1 && B > 2) {           si A <= 1 ir_a L1        MIENTRAS (N < 5) {      L1:  si N >= 5 ir_a L2
    IMPRIMIR("ambas");          si B <= 2 ir_a L1            N++;                     N = N + 1
}                               imprimir "ambas"         }                            ir_a L1
                           L1:                                                   L2:
```

* `SI / CONTRARIO / SINO`: cada bloque salta al final con `ir_a`, **salvo el último**.
* `PARA`: la inicialización queda fuera del ciclo y el paso se emite después del cuerpo.
* `SEGUN`: los cuerpos primero y las pruebas `si_igual` juntas al final (Aho §6.8); sin
  `DEFECTO`, si ninguna acierta se cae directo a la salida.
* Como **valor** (`BOOL B = A > 1 && C;`) la condición se genera igual y luego se guarda
  `VERDADERO` o `FALSO` en un temporal (Aho §6.6.6).
* Las etiquetas solo se crean si algún salto llega a ellas, y dos seguidas se funden.

**2.3.5 Funciones** (solo documental: EasyScript no tiene funciones). Una llamada
`R = Suma(A, B)` se traduciría con la secuencia de llamada de Aho §6.9:

```
param A                         ; cada argumento, en orden
param B
t1 = llamar Suma, 2             ; 2 = número de argumentos
R = t1                          ; y dentro de Suma:  retornar t
```

Cada llamada recibe un **registro de activación** en la pila (Aho §7.2.3, figura 7.5):
parámetros reales, valor devuelto, enlace de control, enlace de acceso, estado de la máquina
guardado, **datos locales** y **temporales**. Las direcciones que EasyCompiler ya asigna
(datos desde 0 y temporales a continuación) son justamente las dos últimas partes.

**2.3.6 Estructuras.** El elemento `V[i]` está en `i * ancho` bytes y `M[i][j]` en
`(i * columnas + j) * ancho` (Aho §6.4.3). Leer es `=[]` y escribir `[]=`; la carga inicial
usa desplazamientos constantes. `M[1][0]++` calcula la dirección **una vez** y la usa para leer
y para escribir; `LEER(V[i])` lee a un temporal y lo guarda en el elemento.

```
MATRIZ ENT M[2][2] = {{1,2},{3,4}};    M[0] = 1   M[4] = 2   M[8] = 3   M[12] = 4
M[1][0]++;                             t3 = 1 * 2      t5 = t4 * 4     t7 = t6 + 1
                                       t4 = t3 + 0     t6 = M[t5]      M[t5] = t7
```

## 4. Cómo se prueba

`pruebas_u2.ps1` compila con `bin\lanzador.ps1 --compilar` y guarda, por cada programa de prueba, la consola,
los cuádruplos y la **traza**: lo que el código hace al ejecutarlo en `SimuladorCuadruplos`.
Con `-Comparar` muestra las diferencias contra la línea base. Un cambio de esta unidad puede
cambiar los cuádruplos, pero la traza solo puede cambiar donde corrige un defecto.
`VerificadorIntermedio` comprueba además que todo salto tenga destino e informa la *forma del
código* (saltos y etiquetas sobrantes). Programas dedicados: `Tests\intermedio_escalares.easy`,
`Tests\intermedio_esquemas.easy` e `Tests\intermedio_control.easy`.

## 5. Qué cambió en esta unidad

| Antes | Ahora |
|---|---|
| `X` de un bloque interior pisaba a la `X` de fuera | `X$2`: dos nombres distintos |
| `LEER(V[i])` emitía `leer V`; `LEER(M[K][0])` además leía `K` | `leer t` + `V[d] = t`; `K` solo se lee |
| `LEER("msg" + X)` calculaba un `concat` y nunca mostraba el mensaje | `imprimir "msg"` + `leer X` |
| `ENT > DEC`, `"a" + N`, `ARREGLO DEC V = {1}` sin conversión | `ampliar` y `a_texto` explícitos |
| `X++` sobre DEC sumaba `1` | suma `1.0` |
| `SI (A > 1 && B > 2)`: 12 instrucciones | 4 |
| 63 saltos y etiquetas sobrantes en los programas de prueba | 1 (un `PARA` mal escrito a propósito) |
| 1137 instrucciones en los 29 programas | 1008 (−11 %); `Tests\cortocircuito.easy` de 75 a 37 |
| Temporales sin dirección | dirección tras los datos, tabla con `-Codigo` |

Fuera de alcance: emitir prefija, polaca, código P o triplos; funciones en el lenguaje;
reutilizar temporales; cualquier optimización de la Unidad 3.
