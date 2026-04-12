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

        Formato:
        ARREGLO TipoDato Variable[Tamaño];
        Ejemplo:
        ARREGLO ENT Calificaciones[10];

4. Declaración de Matrices

        Formato:
        MATRIZ TipoDato Variable[Filas][Columnas];
        Ejemplo:
        MATRIZ DEC Tablero[5][5];

5. Asignación de Valores

        Formato:
        Variable = ExpresionAritmetica;
        Ejemplo:
        Numero = Numero + 1;

6. Operaciones Matemáticas

    ***Formato:***
        
        Valor Operador Valor
        Operadores: +, -, *, /, %, **
        
    ***Ejemplo:***

        Resultado = Numero1 + Numero2;

7. Condicionales

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

8. Condicional SEGUN (Switch)

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

        Valor OperadorRelacional Valor

***Operadores Relacionales:***

         >, <, >=, <=, ==, !=

***Condición Compuesta:***

        CondicionSimple OperadorLógico CondicionSimple

***Operadores Lógicos:***

         &&, ||, !!
