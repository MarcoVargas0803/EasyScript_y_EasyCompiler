# EasyScript para VS Code

Resaltado de sintaxis para los programas de **EasyScript** (`.easy`), el lenguaje de
EasyCompiler.

- Palabras reservadas: `INICIO`, `FIN`, `SI`, `SINO`, `SEGUN`, `PARA`, `MIENTRAS`, ...
- Tipos: `ENT`, `DEC`, `BOOL`, `LETRA`, `TXT`, `ARREGLO`, `MATRIZ`
- Funciones: `IMPRIMIR`, `LEER`
- Constantes (`CONST_Nombre`), variables, números, textos y comentarios
- Operadores, incluidos los que el analizador léxico rechaza (`&`, `|`, `!` sueltos),
  marcados como inválidos

También configura `Ctrl+/` para comentar con `//`, el cierre automático de
`{ } ( ) [ ] " '` y el plegado de bloques.

## Icono

`icons\easy.svg` es el icono de los archivos `.easy`. Es la versión de 32×32 de
`icons\logo_easyCompiler.svg`, con los trazos en blanco explícito: el original usa
`currentColor`, que VS Code pinta en negro. Además los trazos son un poco más
gruesos (2.5) para que la "E" se lea a 16 px.

- Con **Material Icon Theme**, `bin\instalar.ps1` lo copia a
  `%USERPROFILE%\.vscode\extensions\icons\` y agrega la asociación
  `"*.easy": "../../icons/easy"` en el `settings.json` de usuario.
- Con otros temas, VS Code usa el icono que declara esta extensión en `package.json`.

## Instalación

Se instala junto con el comando `easy`, desde la carpeta `EasyCompiler`:

```powershell
powershell -ExecutionPolicy Bypass -File bin\instalar.ps1
```

O solo la extensión:

```powershell
code --install-extension vscode-easyscript\easyscript.vsix
```

## Volver a empaquetar

Después de cambiar la gramática (`syntaxes\easyscript.tmLanguage.json`), desde esta
carpeta:

```powershell
npx --yes @vscode/vsce package --skip-license --allow-missing-repository --out easyscript.vsix
```

y sube el `.vsix` nuevo al repositorio. Los colores los pone el tema de VS Code.
