# EasyCompiler

Compilador del lenguaje **EasyScript**. Implementa las fases de **análisis
léxico**, **análisis sintáctico** y **análisis semántico**, incluido el **esquema
de traducción** a código de tres direcciones. Imprime el **árbol sintáctico**
decorado con sus operadores y tipos, la **tabla de símbolos** con las direcciones
de memoria de cada dato, y un reporte de errores de las tres fases.

El código intermedio se genera siempre pero no se imprime, porque esa parte
todavía es preliminar. Para verlo:

```powershell
.\build.ps1 -Ejecutar traduccion.txt -Codigo
```

Dos reglas del lenguaje que conviene saber antes de escribir código:

* **Hay que declarar antes de usar.** El análisis recorre el programa de arriba
  abajo, asi que una variable usada antes de su declaración es un error.
* **`ENT` se convierte a `DEC` sin avisar, pero `DEC` a `ENT` es un error**,
  porque perdería los decimales en silencio.

---

# Cómo compilar y ejecutar

## Requisitos

| Herramienta | Versión usada | Notas |
|---|---|---|
| JDK | 21 o superior | El proyecto está configurado con `openjdk-21` |
| JavaCC | 7.0.13 | Incluye `jjtree`, que es el que construye el árbol |

Tanto `java`/`javac` como `javacc`/`jjtree` deben estar en el `PATH`. Los scripts
`javacc.bat` y `jjtree.bat` llaman internamente a `java`, así que si el JDK no está
en el `PATH` fallan aunque ellos sí se encuentren.

Para configurarlo en la sesión actual de PowerShell:

```powershell
$env:JAVA_HOME = "C:\Users\carme\.jdks\openjdk-21.0.2"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Comprueba con `java -version` antes de continuar.

## Sintaxis general

La construcción tiene **tres pasos**, porque la gramática se escribe en un archivo
`.jjt` que primero procesa JJTree:

```
EasyCompiler.jjt  --jjtree-->  EasyCompiler.jj  --javacc-->  *.java  --javac-->  *.class
```

```bash
jjtree EasyCompiler.jjt          # 1. Genera la gramática anotada + las clases del árbol
javacc EasyCompiler.jj           # 2. Genera el analizador léxico y sintáctico
javac -encoding UTF-8 *.java     # 3. Compila todo el proyecto
java Main <archivo>              # 4. Analiza un código fuente de EasyScript
```

> **Edita siempre `EasyCompiler.jjt`, nunca `EasyCompiler.jj`.**
> El `.jj` lo genera `jjtree` y se sobrescribe en cada compilación.
> Si te saltas el paso 1, `javacc` no marca error: simplemente vuelve a compilar la
> versión anterior de la gramática.

## Variante: ver la derivación completa

```bash
java Main <archivo> --arbol-completo
```

Por defecto el árbol se imprime **colapsado**: se omiten los eslabones intermedios de
la cascada de precedencia que no aportan nada, para que el árbol sea legible. Con
`--arbol-completo` se imprime la **derivación literal** de la gramática, mostrando
todos los no terminales que se atraviesan.

La diferencia, para la sentencia `ENT A = 5;`:

**Colapsado (por defecto)**

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

**Con `--arbol-completo`**

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

Y produce:

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
  (usa --arbol-completo para ver la derivacion sin colapsar)

>> ¡Excelente! No se encontraron errores en el código fuente.
```

Si el código tiene errores, en lugar del mensaje final aparece el reporte de errores
con tipo, línea, columna, detalle y un consejo para cada uno.

## Atajo: `build.ps1`

El script encadena los tres pasos para que no se te olvide el primero:

```powershell
.\build.ps1                                    # Solo compila
.\build.ps1 -Ejecutar codigo1.txt              # Compila y analiza
.\build.ps1 -Ejecutar codigo1.txt -Completo    # Además, derivación sin colapsar
```

> Durante el paso de `javacc` aparecen **7 advertencias de `Choice conflict`**.
> Son esperadas: son consecuencia de la recuperación de errores por inserción.
> Ver `plan_correcciones.md`, punto 12.

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
