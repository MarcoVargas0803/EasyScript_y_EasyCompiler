# EasyCompiler

Compilador del lenguaje **EasyScript**. Implementa las fases de **análisis
léxico**, **análisis sintáctico** y **análisis semántico**, incluido el **esquema
de traducción** a código de tres direcciones.

# Guía de Compilación y Ejecución

## Requisitos

| Herramienta | Versión usada | Como suele aparecer en el equipo |
| ----------- | ------------- | -------------------------------- |
| JDK         | 21 o superior | `...\.jdks\openjdk-21.0.2`       |
| JavaCC      | 7.0.13        | `...\javacc-javacc-7.0.13`       |

## Comando `easy`

### Instalación del comando `easy` (Realizarlo solo la primera vez).

Desde la carpeta `EasyCompiler`:

```powershell
powershell -ExecutionPolicy Bypass -File bin\instalar.ps1
```

Agrega la carpeta `bin\` al `PATH` **de usuario**. El del sistema no se toca y no hace
falta ser administrador. Antes guarda un respaldo del valor anterior en
`bin\respaldo_path_<fecha>.txt`. Después hay que **abrir una terminal nueva**; en
VS Code hay que cerrarlo por completo y volver a abrirlo, porque sus terminales
heredan el entorno de cuando arrancó.

El mismo script instala en VS Code la extensión **EasyScript**
(`vscode-easyscript\easyscript.vsix`). Con ella los archivos `.easy` se ven con
resaltado de sintaxis, `Ctrl+/` comenta con `//` y las llaves y comillas se cierran
solas. Si VS Code no está instalado, el script solo lo avisa y sigue. Cómo
modificarla o volver a empaquetarla: `vscode-easyscript\README.md`.

Para quitar el comando y la extensión:

```powershell
powershell -ExecutionPolicy Bypass -File bin\instalar.ps1 -Desinstalar
```

### Funcionamiento del comando `easy`

`easy` compila el compilador cuando hace falta y analiza el programa que le pases.
Funciona igual en PowerShell y en CMD, desde cualquier carpeta:

```powershell
easy <archivo> [banderas ...]
```

### Tabla de banderas y --ayuda

El **árbol sintáctico** (decorado con sus operadores y tipos), la **tabla de
símbolos** (con las direcciones de memoria de cada dato) y el **código intermedio**
(cuádruplos y temporales) se construyen siempre, pero solo se imprimen si se piden
con una bandera:

| Bandera                       | Muestra                                          |
| ----------------------------- | ------------------------------------------------ |
| _(ninguna)_                   | solo errores y advertencias                      |
| `--arbol-sintactico`          | el árbol sintáctico, colapsado                   |
| `--arbol-sintactico-completo` | el árbol con la derivación literal, sin colapsar |
| `--tabla-simbolos`            | la tabla de símbolos                             |
| `--codigo-intermedio`         | los cuádruplos y la dirección de cada temporal   |

Esta misma tabla la imprime el compilador con `--ayuda`:

| Comando                  | Muestra (sin analizar nada)                         |
| ------------------------ | --------------------------------------------------- |
| `easy --ayuda`           | la sintaxis de uso, ejemplos y la tabla de banderas |
| `easy <archivo> --ayuda` | solo la tabla de banderas                           |

**Ejemplo**

```powershell
easy muestra.easy                         # Solo errores y advertencias
easy muestra.easy --arbol-sintactico      # + árbol sintáctico colapsado
easy muestra.easy --arbol-sintactico-completo   # + árbol sin colapsar
easy muestra.easy --tabla-simbolos        # + tabla de símbolos
easy muestra.easy --codigo-intermedio     # + código de tres direcciones
easy --compilar                                 # solo compila
easy --ayuda                                    # sintaxis y tabla de banderas
```

Las banderas también se pueden combinar:

```powershell
easy traduccion.easy --arbol-sintactico --tabla-simbolos --codigo-intermedio
```

# Otras alternativas de compilación

## Opción A — los cuatro comandos a mano, con la sesión preparada

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
jjtree -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jjt   # 1. gramática anotada + clases del árbol
javacc -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jj    # 2. analizador léxico y sintáctico
javac -encoding UTF-8 -d out backend\*.java frontend\*.java # 3. compila todo a la carpeta out\
java "-Dfile.encoding=UTF-8" -cp out frontend.Main Tests\muestra.easy   # 4. analiza un programa
```

**CMD**

```bat
REM 1. gramática anotada + clases del árbol
jjtree -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jjt
REM 2. analizador léxico y sintáctico
javacc -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jj
REM 3. compila todo a la carpeta out\
javac -encoding UTF-8 -d out backend\*.java frontend\*.java
REM 4. analiza un programa
java -Dfile.encoding=UTF-8 -cp out frontend.Main Tests\muestra.easy
```

Qué esperar de cada paso:

| Paso | Salida correcta                                                       |
| ---- | --------------------------------------------------------------------- |
| 1    | `Annotated grammar generated successfully in backend\EasyCompiler.jj` |
| 2    | `Parser generated successfully.` (sin advertencias)                   |
| 3    | **nada**: si `javac` no dice nada, compiló bien                       |
| 4    | el reporte de errores (árbol y tabla, con sus banderas)               |

El paso 2 no debe dar **ninguna advertencia**. Si aparece un `Choice conflict`,
la gramática cambió sin querer (ver la nota sobre las advertencias de `javacc`).

El `-encoding UTF-8` del paso 3 no es opcional: el código tiene acentos y sin él
`javac` falla en un equipo configurado en español.

El `-d out` manda los `.class` a `out\backend\` y `out\frontend\` en vez de
dejarlos mezclados con los `.java`. Por eso el paso 4 lleva `-cp out`: es donde quedaron.

## Opción B — rutas completas, sin tocar el `PATH`

Si prefieres no modificar nada del entorno, esto funciona siempre. Las dos primeras
líneas solo definen atajos para no repetir la ruta entera:

```powershell
$J  = "C:\Users\carme\.jdks\openjdk-21.0.2\bin"
$CC = "C:\javacc\javacc-javacc-7.0.13\target\javacc.jar"

& "$J\java.exe"  -cp $CC jjtree -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jjt
& "$J\java.exe"  -cp $CC javacc -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jj
& "$J\javac.exe" -encoding UTF-8 -d out backend\*.java frontend\*.java
& "$J\java.exe"  "-Dfile.encoding=UTF-8" -cp out frontend.Main Tests\muestra.easy
```

## Si la terminal no reconoce los comandos

Es el tropiezo más común, y casi siempre es lo mismo: **`javacc` y `jjtree` sí están
en el `PATH`, pero `java` y `javac` no.**

La trampa está en que `javacc.bat` por dentro es una sola línea:

```powershell
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

> **`%JAVA_HOME%\bin` en el `PATH` y aun así no encuentra `java`:** en el registro,
> el `PATH` de usuario puede estar guardado como texto simple (`REG_SZ`) en vez de
> texto expandible (`REG_EXPAND_SZ`). Con `REG_SZ`, Windows no reemplaza
> `%JAVA_HOME%` por la ruta del JDK y la entrada no sirve. `bin\instalar.ps1` lo deja
> como `REG_EXPAND_SZ` sin cambiar su contenido. Para revisarlo:
>
> ```powershell
> [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey('Environment').GetValueKind('Path')
> ```
>
> Debe decir `ExpandString`.

> **Sobre las variables de `bin\lanzador.ps1`:** dentro de ese script hay `$java`, `$javac` y
> `$JAVACC`. Son variables **locales al script**: existen mientras se ejecuta y
> desaparecen al terminar. Si copias una línea del script y la pegas en una
> terminal, `$java` está vacío y el comando no hace nada. No es que "PowerShell no
> reconozca las variables": es que ahí no existen.

# Proceso de contribución

La construcción tiene **tres pasos**, porque la gramática se escribe en un archivo
`.jjt` que primero procesa JJTree:

```
EasyCompiler.jjt  --jjtree-->  EasyCompiler.jj  --javacc-->  *.java  --javac-->  *.class
```

> **Edita siempre `EasyCompiler.jjt`, nunca `EasyCompiler.jj`.**
> El `.jj` lo genera `jjtree` y se sobrescribe en cada compilación.
> Si te saltas el paso 1, `javacc` no marca error: simplemente vuelve a compilar la
> versión anterior de la gramática.

# Un programa de muestra

`Tests\muestra.easy` es un programa pequeño y correcto, pensado para comprobar que la
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

# Estructuras Gramaticales y Sintácticas Aceptadas

## Sintaxis del lenguaje

1.  Programa Principal

    **_Estructura General:_**

        INICIO {
            Sentencias
        } FIN

2.  Declaración de Variables

    **_Formato:_**

        TipoDato Variable = Valor;

    **_Ejemplo:_**

        ENT Numero = 5;

3.  Declaración de Arreglos

    **_Formato:_**

         ARREGLO TipoDato Variable[Tamaño];

    **_Ejemplo:_**

         ARREGLO ENT Calificaciones[10];

4.  Declaración de Matrices

    **_Formato:_**

         MATRIZ TipoDato Variable[Filas][Columnas];

    **_Ejemplo:_**

         MATRIZ DEC Tablero[5][5];

5.  Asignación de Valores

    **_Formato:_**

         Variable = ExpresionAritmetica;

    **_Ejemplo:_**

         Numero = Numero + 1;

6.  Operaciones Matemáticas

    **_Formato:_**

        Valor Operador Valor
        Operadores: +, -, *, /, %, **

    **_Ejemplo:_**

        Resultado = Numero1 + Numero2;

7.  Condicionales

    **_Formato:_**

        SI (Condicion) {
            Sentencias
        } CONTRARIO (Condicion) {
            Sentencias
        } SINO {
            Sentencias
        }

    **_Ejemplo:_**

        SI (Numero > 10) {
            IMPRIMIR("Mayor a 10");
        } SINO {
            IMPRIMIR("Menor o igual a 10");
        }

8.  Condicional SEGUN (Switch)

**_Formato:_**

        SEGUN (Variable) {
            CASO Valor:
                Sentencias
                DETENER;
            DEFECTO:
                Sentencias
        }

**_Ejemplo:_**

        SEGUN (Opcion) {
            CASO 1:
                IMPRIMIR("Opción 1");
                DETENER;
            DEFECTO:
                IMPRIMIR("Opción no válida");
        }

9. Ciclo PARA

**_Formato:_**

        PARA (DeclaracionVariable; Condicion; Incremento/Decremento) {
            Sentencias
        }

**_Ejemplo:_**

        PARA (ENT i = 0; i < 10; i++) {
            IMPRIMIR(i);
        }

10. Ciclo MIENTRAS

**_Formato:_**

        MIENTRAS (Condicion) {
            Sentencias
        }

**_Ejemplo:_**

        MIENTRAS (Numero < 100) {
            Numero = Numero * 2;
        }

11. Entrada y Salida

**_Imprimir:_**

        IMPRIMIR("Texto" + Variable);

**_Leer:_**

        LEER(Variable);

12. Condiciones

**_Condición Simple:_**

**Formato:**

        Valor OperadorRelacional Valor

**Ejemplo:**

        5 > 3

**_Operadores Relacionales:_**

         >, <, >=, <=, ==, !=

**_Condición Compuesta:_**

**Formato:**

        CondicionSimple OperadorLógico CondicionSimple

**Ejemplo**

           ( 16 > 8 ) && (16 < 10 )

**_Operadores Lógicos:_**

         &&, ||, !!
