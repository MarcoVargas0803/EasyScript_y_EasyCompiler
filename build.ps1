# build.ps1 - Compila EasyCompiler desde cero.
#
# Ahora la construccion tiene TRES pasos, porque JJTree se agrego al frente:
#
#   EasyCompiler.jjt  --jjtree-->  EasyCompiler.jj  --javacc-->  *.java  --javac-->  *.class
#
# El unico archivo de gramatica que se edita es EasyCompiler.jjt.
# EasyCompiler.jj pasa a ser generado: cualquier cambio hecho ahi se pierde.
#
# Las fuentes viven en dos paquetes: backend\ (gramatica, analisis y codigo
# intermedio, ahi se genera todo) y frontend\ (Main e ImpresorArbol). Los .class
# quedan en out\backend\ y out\frontend\, y el programa se ejecuta como frontend.Main.
#
# Uso:
#   .\build.ps1                      Compila
#   .\build.ps1 -Ejecutar Tests\codigo1.easy             Compila y analiza ese archivo (solo errores)
#   .\build.ps1 -Ejecutar Tests\codigo1.easy -Arbol      Ademas muestra el arbol sintactico
#   .\build.ps1 -Ejecutar Tests\codigo1.easy -Completo   Ademas muestra el arbol con la derivacion sin colapsar
#   .\build.ps1 -Ejecutar Tests\codigo1.easy -Tabla      Ademas muestra la tabla de simbolos
#   .\build.ps1 -Ejecutar Tests\codigo1.easy -Codigo     Ademas muestra el codigo de tres direcciones
#
# Los switches se combinan. El arbol, la tabla y el codigo intermedio se generan
# siempre, pero solo se imprimen si se piden: la salida normal son los errores.

param(
    [string] $Ejecutar = "",
    [switch] $Arbol,
    [switch] $Completo,
    [switch] $Tabla,
    [switch] $Codigo
)

$ErrorActionPreference = "Stop"

# Ruta al jar de JavaCC. Ajustala si cambias de equipo.
$JAVACC = "C:\javacc\javacc-javacc-7.0.13\target\javacc.jar"

# JDK a usar. Se prefiere el que declara el proyecto en .idea\misc.xml (openjdk-21)
# para que la terminal y IntelliJ compilen con la misma version; los demas son
# respaldo. Si ya tienes JAVA_HOME configurado, ese gana.
$JDKS = @(
    $env:JAVA_HOME,
    "C:\Users\carme\.jdks\openjdk-21.0.2",
    "C:\Program Files\Java\jdk-22",
    "C:\Program Files\Java\jdk-24"
)

$java = $null
foreach ($base in $JDKS) {
    if ([string]::IsNullOrWhiteSpace($base)) { continue }
    $candidato = Join-Path $base "bin\java.exe"
    if (Test-Path $candidato) { $java = $candidato; $JDK = Join-Path $base "bin"; break }
}

if (-not $java) {
    Write-Error "No se encontro ningun JDK. Define JAVA_HOME o edita `$JDKS al inicio de build.ps1."
}

$javac = Join-Path $JDK "javac.exe"

foreach ($ruta in @($java, $javac, $JAVACC)) {
    if (-not (Test-Path $ruta)) {
        Write-Error "No se encontro: $ruta`nRevisa las rutas al inicio de build.ps1."
    }
}

# java -version escribe en stderr. Con "Stop", Windows PowerShell 5.1 lo toma
# como error (NativeCommandError) y corta el script; por eso aqui se relaja solo
# dentro de este bloque. En pwsh 7 no hace falta, pero tampoco estorba.
$versionJdk = & { $ErrorActionPreference = "Continue"; & $java -version 2>&1 } | Select-Object -First 1
Write-Host ("JDK: " + $versionJdk) -ForegroundColor DarkGray

Set-Location $PSScriptRoot

Write-Host "[1/3] jjtree  backend\EasyCompiler.jjt -> backend\EasyCompiler.jj" -ForegroundColor Cyan
& $java -cp $JAVACC jjtree -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jjt
if ($LASTEXITCODE -ne 0) { Write-Error "jjtree fallo." }

Write-Host "[2/3] javacc  backend\EasyCompiler.jj -> backend\*.java" -ForegroundColor Cyan
# No debe salir ninguna advertencia. Las 7 de 'Choice conflict' que habia se
# resolvieron con LOOKAHEAD(1) en EasyCompiler.jjt (ver la jerarquia de
# expresiones); si vuelve a salir una, la gramatica cambio sin querer.
& $java -cp $JAVACC javacc -OUTPUT_DIRECTORY:backend backend\EasyCompiler.jj
if ($LASTEXITCODE -ne 0) { Write-Error "javacc fallo." }

Write-Host "[3/3] javac   backend\*.java frontend\*.java -> out\" -ForegroundColor Cyan
& $javac -encoding UTF-8 -d out backend\*.java frontend\*.java
if ($LASTEXITCODE -ne 0) { Write-Error "javac fallo." }

Write-Host "Compilacion terminada." -ForegroundColor Green

if ($Ejecutar -ne "") {
    Write-Host ""
    $banderas = @()
    if ($Completo) { $banderas += "--arbol-sintactico-completo" }
    elseif ($Arbol) { $banderas += "--arbol-sintactico" }
    if ($Tabla)    { $banderas += "--tabla-simbolos" }
    if ($Codigo)   { $banderas += "--codigo-intermedio" }
    & $java "-Dfile.encoding=UTF-8" -cp out frontend.Main $Ejecutar @banderas
} else {
    Write-Host "Para analizar un archivo:" -ForegroundColor DarkGray
    Write-Host "  .\build.ps1 -Ejecutar Tests\codigo1.easy" -ForegroundColor DarkGray
}
