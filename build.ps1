# build.ps1 - Compila EasyCompiler desde cero.
#
# Ahora la construccion tiene TRES pasos, porque JJTree se agrego al frente:
#
#   EasyCompiler.jjt  --jjtree-->  EasyCompiler.jj  --javacc-->  *.java  --javac-->  *.class
#
# El unico archivo de gramatica que se edita es EasyCompiler.jjt.
# EasyCompiler.jj pasa a ser generado: cualquier cambio hecho ahi se pierde.
#
# Uso:
#   .\build.ps1                      Compila
#   .\build.ps1 -Ejecutar codigo1.txt          Compila y analiza ese archivo
#   .\build.ps1 -Ejecutar codigo1.txt -Completo   Ademas muestra la derivacion sin colapsar

param(
    [string] $Ejecutar = "",
    [switch] $Completo
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

Write-Host ("JDK: " + (& $java -version 2>&1 | Select-Object -First 1)) -ForegroundColor DarkGray

Set-Location $PSScriptRoot

Write-Host "[1/3] jjtree  EasyCompiler.jjt -> EasyCompiler.jj" -ForegroundColor Cyan
& $java -cp $JAVACC jjtree EasyCompiler.jjt
if ($LASTEXITCODE -ne 0) { Write-Error "jjtree fallo." }

Write-Host "[2/3] javacc  EasyCompiler.jj -> *.java" -ForegroundColor Cyan
# Las 7 advertencias de 'Choice conflict' son esperadas: son el precio de la
# recuperacion por insercion. Ver plan_correcciones.md, punto 12.
& $java -cp $JAVACC javacc EasyCompiler.jj
if ($LASTEXITCODE -ne 0) { Write-Error "javacc fallo." }

Write-Host "[3/3] javac   *.java -> out\" -ForegroundColor Cyan
& $javac -encoding UTF-8 -d out *.java
if ($LASTEXITCODE -ne 0) { Write-Error "javac fallo." }

Write-Host "Compilacion terminada." -ForegroundColor Green

if ($Ejecutar -ne "") {
    Write-Host ""
    if ($Completo) {
        & $java "-Dfile.encoding=UTF-8" -cp out Main $Ejecutar --arbol-completo
    } else {
        & $java "-Dfile.encoding=UTF-8" -cp out Main $Ejecutar
    }
} else {
    Write-Host "Para analizar un archivo:" -ForegroundColor DarkGray
    Write-Host "  .\build.ps1 -Ejecutar codigo1.txt" -ForegroundColor DarkGray
}
