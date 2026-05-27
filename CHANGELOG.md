# Resumen de Cambios (Changelog) - Actualización de Manejo de Errores

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
