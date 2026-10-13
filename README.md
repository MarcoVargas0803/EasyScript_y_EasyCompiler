# EasyCompiler

Compilador del lenguaje **EasyScript**. Implementa las fases de **análisis
léxico**, **análisis sintáctico** y **análisis semántico**, incluido el **esquema
de traducción** a código de tres direcciones. Por defecto imprime solo el
**reporte de errores y advertencias** de las tres fases.

El **árbol sintáctico** (decorado con sus operadores y tipos), la **tabla de
símbolos** (con las direcciones de memoria de cada dato) y el **código intermedio**
(cuádruplos y temporales) se construyen siempre, pero solo se imprimen si se piden
con una bandera:

| Bandera | Muestra |
|---|---|
| *(ninguna)* | solo errores y advertencias |
| `--arbol-sintactico` | el árbol sintáctico, colapsado |
| `--arbol-sintactico-completo` | el árbol con la derivación literal, sin colapsar |
| `--tabla-simbolos` | la tabla de símbolos |
| `--codigo-intermedio` | los cuádruplos y la dirección de cada temporal |

Las banderas van después del archivo y se pueden combinar:

```bash
java Main traduccion.txt --arbol-sintactico --tabla-simbolos --codigo-intermedio
```

Cómo se genera, por qué se eligieron los cuádruplos y el mismo programa escrito en
las demás notaciones del temario (prefija, postfija, polaca, código P, triplos):
**`generacion_ci.md`**.

Dos reglas del lenguaje que conviene saber antes de escribir código:

* **Hay que declarar antes de usar.** El análisis recorre el programa de arriba
  abajo, asi que una variable usada antes de su declaración es un error.
* **`ENT` se convierte a `DEC` sin avisar, pero `DEC` a `ENT` es un error**,
  porque perdería los decimales en silencio.

---

# Cómo compilar y ejecutar

## Requisitos

| Herramienta | Versión usada | Dónde está en este equipo |
|---|---|---|
| JDK | 21 o superior | `C:\Users\carme\.jdks\openjdk-21.0.2` |
| JavaCC | 7.0.13 | `C:\javacc\javacc-javacc-7.0.13` |

## Si la terminal no reconoce los comandos

Es el tropiezo más común, y casi siempre es lo mismo: **`javacc` y `jjtree` sí están
en el `PATH`, pero `java` y `javac` no.**

La trampa está en que `javacc.bat` por dentro es una sola línea:

```bat
java -classpath "%~dp0..\target\javacc.jar" javacc %1 %2 ...
```

O sea que llama a `java`. Si el JDK no está en el `PATH`, `javacc` falla **aunque el
propio `javacc` sí se encuentre**, y el mensaje de error habla de `java`, no de
`javacc`, que despista bastante.

Para comprobar qué ve tu terminal:

```powershell
Get-Command java, javac, javacc, jjtree -ErrorAction SilentlyContinue | Select-Object Name, Source
```

Lo que no aparezca en esa lista es lo que falta.

> **Sobre las variables de `build.ps1`:** dentro de ese script hay `$java`, `$javac` y
> `$JAVACC`. Son variables **locales al script**: existen mientras se ejecuta y
> desaparecen al terminar. Si copias una línea de `build.ps1` y la pegas en una
> terminal, `$java` está vacío y el comando no hace nada. No es que "PowerShell no
> reconozca las variables": es que ahí no existen.

## Opción A — preparar la sesión y usar los comandos cortos

Esto hay que hacerlo **una vez en cada terminal que abras**. No es permanente.

**PowerShell**

```powershell
$env:JAVA_HOME = "C:\Users\carme\.jdks\openjdk-21.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version          # comprueba antes de seguir
```

**CMD**

```bat
set JAVA_HOME=C:\Users\carme\.jdks\openjdk-21.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
java -version
```

Con eso ya funcionan los cuatro pasos, desde la carpeta `EasyCompiler`:

**PowerShell**

```powershell
jjtree EasyCompiler.jjt                 # 1. gramática anotada + clases del árbol
javacc EasyCompiler.jj                  # 2. analizador léxico y sintáctico
javac -encoding UTF-8 -d out *.java     # 3. compila todo a la carpeta out\
java "-Dfile.encoding=UTF-8" -cp out Main muestra.txt   # 4. analiza un programa
```

> En PowerShell el `-Dfile.encoding=UTF-8` va **entre comillas**: sin ellas,
> PowerShell parte el argumento en el punto y Java recibe `-Dfile` y `.encoding=UTF-8`
> por separado (falla con *Could not find or load main class .encoding=UTF-8*).

**CMD**

```bat
REM 1. gramática anotada + clases del árbol
jjtree EasyCompiler.jjt
REM 2. analizador léxico y sintáctico
javacc EasyCompiler.jj
REM 3. compila todo a la carpeta out\
javac -encoding UTF-8 -d out *.java
REM 4. analiza un programa
java -Dfile.encoding=UTF-8 -cp out Main muestra.txt
```

En CMD los comentarios van en su propia línea: un `::` o un `REM` al final de un
comando no es un comentario, sino argumentos de más para ese comando.

Qué esperar de cada paso:

| Paso | Salida correcta |
|---|---|
| 1 | `Annotated grammar generated successfully in .\EasyCompiler.jj` |
| 2 | `Parser generated successfully.` (sin advertencias) |
| 3 | **nada**: si `javac` no dice nada, compiló bien |
| 4 | el reporte de errores (árbol y tabla, con sus banderas) |

El paso 2 no debe dar **ninguna advertencia**. Si aparece un `Choice conflict`,
la gramática cambió sin querer (ver la nota sobre las advertencias de `javacc`).

El `-encoding UTF-8` del paso 3 no es opcional: el código tiene acentos y sin él
`javac` falla en un equipo configurado en español.

El `-d out` manda los `.class` a `out\` en vez de dejarlos mezclados con los
`.java`. Por eso el paso 4 lleva `-cp out`: es donde quedaron.

## Opción B — rutas completas, sin tocar el `PATH`

Si prefieres no modificar nada del entorno, esto funciona siempre. Las dos primeras
líneas solo definen atajos para no repetir la ruta entera:

```powershell
$J  = "C:\Users\carme\.jdks\openjdk-21.0.2\bin"
$CC = "C:\javacc\javacc-javacc-7.0.13\target\javacc.jar"

& "$J\java.exe"  -cp $CC jjtree EasyCompiler.jjt
& "$J\java.exe"  -cp $CC javacc EasyCompiler.jj
& "$J\javac.exe" -encoding UTF-8 -d out *.java
& "$J\java.exe"  "-Dfile.encoding=UTF-8" -cp out Main muestra.txt
```

Fíjate en que `jjtree` y `javacc` no son programas: son **clases dentro de
`javacc.jar`**. Por eso se invocan como `java -cp javacc.jar jjtree`, que es
exactamente lo que hacen los `.bat` por dentro.

El `&` del principio es el operador de llamada de PowerShell, necesario cuando la
ruta del ejecutable está en una variable o lleva espacios.

## Opción C — el script (lo habitual)

`build.ps1` hace los tres pasos de compilación, busca el JDK solo y no depende del
`PATH`:

```powershell
.\build.ps1                                      # solo compila
.\build.ps1 -Ejecutar muestra.txt                # compila y analiza (solo errores)
.\build.ps1 -Ejecutar muestra.txt -Arbol         # + árbol sintáctico
.\build.ps1 -Ejecutar muestra.txt -Completo      # + árbol con la derivación sin colapsar
.\build.ps1 -Ejecutar muestra.txt -Tabla         # + tabla de símbolos
.\build.ps1 -Ejecutar muestra.txt -Codigo        # + código de tres direcciones
```

Si PowerShell se niega a ejecutarlo por la política de scripts:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Ejecutar muestra.txt
```

## Sintaxis general

La construcción tiene **tres pasos**, porque la gramática se escribe en un archivo
`.jjt` que primero procesa JJTree:

```
EasyCompiler.jjt  --jjtree-->  EasyCompiler.jj  --javacc-->  *.java  --javac-->  *.class
```

> **Edita siempre `EasyCompiler.jjt`, nunca `EasyCompiler.jj`.**
> El `.jj` lo genera `jjtree` y se sobrescribe en cada compilación.
> Si te saltas el paso 1, `javacc` no marca error: simplemente vuelve a compilar la
> versión anterior de la gramática, y te vuelves loco buscando por qué tu cambio no
> surte efecto.

## Un programa de muestra

`muestra.txt` es un programa pequeño y correcto, pensado para comprobar que la
compilación quedó bien: tiene una declaración, un ciclo, un condicional y salidas,
así que el árbol, la tabla de símbolos y el código intermedio salen con contenido
cuando se piden con sus banderas.

```
INICIO{
    ENT Contador = 0;
    ENT Limite = 3;

    MIENTRAS (Contador < Limite) {
        IMPRIMIR("vuelta " + Contador);
        Contador = Contador + 1;
    }

    SI (Contador == Limite) {
        IMPRIMIR("Termino en " + Contador);
    } SINO {
        IMPRIMIR("Algo salio mal");
    }
}FIN
```

Debe terminar con `¡Excelente! No se encontraron errores en el código fuente.`
Si sale eso, las cuatro herramientas están bien encadenadas.

## Variante: ver la derivación completa

```bash
java Main <archivo> --arbol-sintactico-completo
```

Con `--arbol-sintactico` el árbol se imprime **colapsado**: se omiten los eslabones
intermedios de la cascada de precedencia que no aportan nada, para que el árbol sea
legible. Con `--arbol-sintactico-completo` se imprime la **derivación literal** de la
gramática, mostrando todos los no terminales que se atraviesan.

La diferencia, para la sentencia `ENT A = 5;`:

**Con `--arbol-sintactico` (colapsado)**

```
DeclaracionVariable
├─ TipoDato
│  └─ <ENTERO_DATO: "ENT">
├─ <VARIABLE: "A">
├─ <IGUAL_ASIGNACION: "=">
├─ Valor
│  └─ <NUM_ENTERO: "5">
└─ PuntoYComaVirtual
   └─ <PUNTO_COMA: ";">
```

**Con `--arbol-sintactico-completo`**

```
DeclaracionVariable
├─ TipoDato
│  └─ <ENTERO_DATO: "ENT">
├─ <VARIABLE: "A">
├─ <IGUAL_ASIGNACION: "=">
├─ Condicion
│  └─ CondicionSimple
│     └─ ExpresionAritmetica
│        └─ ExpresionNivel1
│           └─ ExpresionNivel2
│              └─ ExpresionNivel3
│                 └─ ExpresionNivel4
│                    └─ ValorConArreglos
│                       └─ Valor
│                          └─ <NUM_ENTERO: "5">
└─ PuntoYComaVirtual
   └─ <PUNTO_COMA: ";">
```

## Ejemplo completo

Partiendo del archivo `Hello world.txt`:

```
INICIO{
    IMPRIMIR("HOLA MUNDO EN EASYSCRIPT");
}FIN
```

Se compila y ejecuta así:

```bash
jjtree EasyCompiler.jjt
javacc EasyCompiler.jj
javac -encoding UTF-8 *.java
java Main "Hello world.txt"
```

Y produce solo el resultado:

```
>> Analisis completado.
>> ¡Excelente! No se encontraron errores en el código fuente.
  (usa --arbol-sintactico, --tabla-simbolos o --codigo-intermedio para ver mas detalle)
```

Para ver además el árbol, se vuelve a ejecutar con la bandera:

```bash
java Main "Hello world.txt" --arbol-sintactico
```

```
>> Analisis completado.

============================================================
 🌳 ARBOL SINTACTICO (colapsado)
============================================================
Programa
├─ <INICIO_PROGRAMA: "INICIO">
├─ <ABRIR_LLAVE: "{">
├─ Sentencias
│  └─ Imprimir
│     ├─ <IMPRIMIR_FUNCION: "IMPRIMIR">
│     ├─ <ABRIR_PARENTESIS: "(">
│     ├─ Valor
│     │  └─ <CADENA: "\"HOLA MUNDO EN EASYSCRIPT\"">
│     ├─ <CERRAR_PARENTESIS: ")">
│     └─ PuntoYComaVirtual
│        └─ <PUNTO_COMA: ";">
├─ <CERRAR_LLAVE: "}">
├─ <FIN_PROGRAMA: "FIN">
└─ <EOF>
  (usa --arbol-sintactico-completo para ver la derivacion sin colapsar)

>> ¡Excelente! No se encontraron errores en el código fuente.
```

Si el código tiene errores, en lugar del mensaje final aparece el reporte de errores
con tipo, línea, columna, detalle y un consejo para cada uno.

## Nota sobre las advertencias de `javacc`

> `javacc` compila la gramática **sin advertencias**. Antes daba 7 de `Choice
> conflict` (`+`, `*`, `**`, `[`, `[`, `>`, `&&`): ambigüedades reales, casi todas
> creadas por la recuperación de errores por inserción, que JavaCC resolvía de forma
> voraz, que es la correcta. Ahora esa decisión está escrita en la gramática con
> `LOOKAHEAD(1)` en esos 7 puntos; el parser generado es idéntico al de antes.
> Ver el comentario en `EasyCompiler.jjt`, al inicio de la jerarquía de
> expresiones. Si vuelve a aparecer una advertencia, la gramática se modificó sin
> querer.

Las formas de compilar están explicadas arriba, en *Cómo compilar y ejecutar*.

---

# Estructuras Gramaticales y Sintácticas Aceptadas


## Sintaxis del lenguaje

1. Programa Principal

        
    ***Estructura General:***

        INICIO {
            Sentencias
        } FIN

2. Declaración de Variables
        
    ***Formato:***
    
        TipoDato Variable = Valor;

    ***Ejemplo:***
    
        ENT Numero = 5;

3. Declaración de Arreglos

   ***Formato:***
   
        ARREGLO TipoDato Variable[Tamaño];

   ***Ejemplo:***
   
        ARREGLO ENT Calificaciones[10];

5. Declaración de Matrices

   ***Formato:***
   
        MATRIZ TipoDato Variable[Filas][Columnas];

   ***Ejemplo:***
   
        MATRIZ DEC Tablero[5][5];

7. Asignación de Valores

   ***Formato:***
   
        Variable = ExpresionAritmetica;
   ***Ejemplo:***
   
        Numero = Numero + 1;

9. Operaciones Matemáticas

    ***Formato:***
        
        Valor Operador Valor
        Operadores: +, -, *, /, %, **
        
    ***Ejemplo:***

        Resultado = Numero1 + Numero2;

10. Condicionales

    ***Formato:***

        SI (Condicion) {
            Sentencias
        } CONTRARIO (Condicion) {
            Sentencias
        } SINO {
            Sentencias
        }
    
    ***Ejemplo:***

        SI (Numero > 10) {
            IMPRIMIR("Mayor a 10");
        } SINO {
            IMPRIMIR("Menor o igual a 10");
        }

11. Condicional SEGUN (Switch)

***Formato:***

        SEGUN (Variable) {
            CASO Valor:
                Sentencias
                DETENER;
            DEFECTO:
                Sentencias
        }

***Ejemplo:***

        SEGUN (Opcion) {
            CASO 1:
                IMPRIMIR("Opción 1");
                DETENER;
            DEFECTO:
                IMPRIMIR("Opción no válida");
        }
9. Ciclo PARA

***Formato:***

        PARA (DeclaracionVariable; Condicion; Incremento/Decremento) {
            Sentencias
        }

***Ejemplo:***

        PARA (ENT i = 0; i < 10; i++) {
            IMPRIMIR(i);
        }

10. Ciclo MIENTRAS

***Formato:***

        MIENTRAS (Condicion) {
            Sentencias
        }

***Ejemplo:***

        MIENTRAS (Numero < 100) {
            Numero = Numero * 2;
        }
11. Entrada y Salida

***Imprimir:***

        IMPRIMIR("Texto" + Variable);
***Leer:***

        LEER(Variable);

12. Condiciones

***Condición Simple:***
        
**Formato:**

        Valor OperadorRelacional Valor

**Ejemplo:**

        5 > 3

***Operadores Relacionales:***

         >, <, >=, <=, ==, !=

***Condición Compuesta:***

**Formato:**

        CondicionSimple OperadorLógico CondicionSimple

**Ejemplo**

           ( 16 > 8 ) && (16 < 10 )


***Operadores Lógicos:***

         &&, ||, !!
