# pruebas_opt.ps1 - Arnes de regresion de la optimizacion del codigo intermedio.
#
# Corre VerificadorOptimizacion sobre Tests\*.easy y Tests\optimizacion\*.easy.
# Por cada programa sin errores ejecuta el codigo original y el optimizado con
# las fases F1, F1-F2, ... F1-F6, y exige que todos hagan lo mismo (misma
# traza). Ademas guarda, por programa:
#
#   <base>.optimizado.txt  el codigo final
#   <base>.metricas.txt    cada metrica despues de cada fase (F0 = sin optimizar)
#   <base>.bitacora.txt    cada transformacion aplicada y el grafo de flujo final
#
# Uso:
#   .\pruebas_opt.ps1              Crea la linea base en linea_base_opt\
#                                  (se niega si ya existe, para no pisarla)
#   .\pruebas_opt.ps1 -Forzar      Rehace la linea base aunque ya exista
#   .\pruebas_opt.ps1 -Comparar    Captura en actual_opt\ y compara contra la
#                                  linea base, archivo por archivo
#   .\pruebas_opt.ps1 -Hasta 3     Solo aplica F1-F3 (para aislar una fase)
#
# Siempre compila primero con bin\lanzador.ps1 --compilar. Usa rutas completas al
# JDK y NO modifica $env:Path.
#
# Codigo de salida: 0 si todo bien; 1 si falla la compilacion, la linea base ya
# existe (sin -Forzar), alguna version optimizada hace algo distinto o -Comparar
# encuentra diferencias.

param(
    [switch] $Comparar,
    [switch] $Forzar,
    [int] $Hasta = 6
)

$ErrorActionPreference = "Stop"

# JDK: la misma busqueda que bin\lanzador.ps1. JAVA_HOME gana si esta definido.
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
    if (Test-Path $candidato) { $java = $candidato; break }
}

if (-not $java) {
    Write-Host "No se encontro ningun JDK. Define JAVA_HOME o edita `$JDKS al inicio de pruebas_opt.ps1." -ForegroundColor Red
    exit 1
}

$proyecto  = $PSScriptRoot
$dirBase   = Join-Path $proyecto "linea_base_opt"
$dirActual = Join-Path $proyecto "actual_opt"
$tipos     = @("optimizado", "metricas", "bitacora")

# Las diferencias se muestran con el caracter ESC visible.
function Mostrable([string] $linea) {
    return $linea.Replace([string][char]27, "<ESC>")
}

function Mostrar-Diferencia([string] $a, [string] $b) {
    $la = [System.IO.File]::ReadAllLines($a, [System.Text.Encoding]::UTF8)
    $lb = [System.IO.File]::ReadAllLines($b, [System.Text.Encoding]::UTF8)

    $n = [Math]::Min($la.Length, $lb.Length)
    $primera = -1
    for ($i = 0; $i -lt $n; $i++) {
        if ($la[$i] -cne $lb[$i]) { $primera = $i; break }
    }
    if ($primera -lt 0 -and $la.Length -ne $lb.Length) { $primera = $n }
    if ($primera -lt 0) {
        Write-Host "        las lineas coinciden: la diferencia esta en los fines de linea o en bytes invisibles" -ForegroundColor DarkGray
        return
    }
    Write-Host ("        primera linea distinta: " + ($primera + 1) +
                "  (linea base: " + $la.Length + " lineas, actual: " + $lb.Length + ")") -ForegroundColor DarkGray

    $mostradas = 0
    $maximo = [Math]::Max($la.Length, $lb.Length)
    for ($i = $primera; $i -lt $maximo -and $mostradas -lt 5; $i++) {
        $va = if ($i -lt $la.Length) { $la[$i] } else { $null }
        $vb = if ($i -lt $lb.Length) { $lb[$i] } else { $null }
        if ($va -ceq $vb) { continue }
        $mostradas++
        Write-Host ("        linea " + ($i + 1) + ":")
        if ($null -ne $va) { Write-Host ("          base   | " + (Mostrable $va)) } else { Write-Host "          base   | (no existe)" }
        if ($null -ne $vb) { Write-Host ("          actual | " + (Mostrable $vb)) } else { Write-Host "          actual | (no existe)" }
    }
}

$ubicacionOriginal = Get-Location
try {
    Set-Location $proyecto

    if (-not $Comparar -and (Test-Path $dirBase) -and -not $Forzar) {
        Write-Host "La linea base ya existe en linea_base_opt\ y no se va a sobrescribir." -ForegroundColor Yellow
        Write-Host "  Para comparar contra ella:      .\pruebas_opt.ps1 -Comparar" -ForegroundColor Yellow
        Write-Host "  Para rehacerla (se pierde):     .\pruebas_opt.ps1 -Forzar" -ForegroundColor Yellow
        exit 1
    }
    if ($Comparar -and -not (Test-Path $dirBase)) {
        Write-Host "No hay linea base que comparar. Creala primero con .\pruebas_opt.ps1" -ForegroundColor Red
        exit 1
    }

    # -----------------------------------------------------------------------
    # 1. Compilar
    # -----------------------------------------------------------------------
    Write-Host "== Compilando con bin\lanzador.ps1 --compilar ==" -ForegroundColor Cyan
    & (Join-Path $proyecto "bin\lanzador.ps1") --compilar
    if ($LASTEXITCODE -ne 0) {
        Write-Host "La compilacion fallo; no se mide nada." -ForegroundColor Red
        exit 1
    }
    Set-Location $proyecto

    # -----------------------------------------------------------------------
    # 2. Capturar
    # -----------------------------------------------------------------------
    $dir = if ($Comparar) { $dirActual } else { $dirBase }
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    New-Item -ItemType Directory -Force $dir | Out-Null

    Write-Host ""
    Write-Host ("== Ejecutando VerificadorOptimizacion (F1-F" + $Hasta + ") en " + (Split-Path $dir -Leaf) + "\ ==") -ForegroundColor Cyan
    & $java "-Dfile.encoding=UTF-8" -cp out backend.optimizacion.VerificadorOptimizacion -hasta $Hasta -salida $dir | Out-Host
    $fallos = ($LASTEXITCODE -ne 0)
    if ($fallos) {
        Write-Host "ATENCION: alguna version optimizada no hace lo mismo que el original (ver tabla)." -ForegroundColor Red
    }

    if (-not $Comparar) {
        Write-Host ""
        Write-Host "Linea base creada en linea_base_opt\" -ForegroundColor Green
        if ($fallos) { exit 1 }
        exit 0
    }

    # -----------------------------------------------------------------------
    # 3. Comparar contra la linea base
    # -----------------------------------------------------------------------
    Write-Host ""
    Write-Host "== Comparando actual_opt\ contra linea_base_opt\ ==" -ForegroundColor Cyan

    $nombres = @{}
    foreach ($d in @($dirBase, $dirActual)) {
        foreach ($f in (Get-ChildItem -Path $d -Filter *.optimizado.txt -File)) {
            $nombres[$f.Name.Substring(0, $f.Name.Length - ".optimizado.txt".Length)] = $true
        }
    }

    $cambios = @{ optimizado = 0; metricas = 0; bitacora = 0 }
    $archivosConCambios = 0
    foreach ($base in ($nombres.Keys | Sort-Object)) {
        $distintos = @()
        foreach ($t in $tipos) {
            $a = Join-Path $dirBase   ($base + "." + $t + ".txt")
            $b = Join-Path $dirActual ($base + "." + $t + ".txt")
            $ea = Test-Path -LiteralPath $a
            $eb = Test-Path -LiteralPath $b
            if (-not $ea -and -not $eb) { continue }
            if ($ea -and $eb) {
                $ha = (Get-FileHash -LiteralPath $a -Algorithm SHA256).Hash
                $hb = (Get-FileHash -LiteralPath $b -Algorithm SHA256).Hash
                if ($ha -eq $hb) { continue }
            }
            $distintos += $t
        }
        if ($distintos.Count -eq 0) {
            Write-Host ("  [igual]   " + $base + ".easy") -ForegroundColor DarkGray
            continue
        }
        $archivosConCambios++
        Write-Host ("  [CAMBIO]  " + $base + ".easy  ->  " + ($distintos -join ", ")) -ForegroundColor Yellow
        foreach ($t in $distintos) {
            $cambios[$t]++
            $a = Join-Path $dirBase   ($base + "." + $t + ".txt")
            $b = Join-Path $dirActual ($base + "." + $t + ".txt")
            Write-Host ("      " + $t.ToUpper() + ":")
            if (-not (Test-Path -LiteralPath $a)) {
                Write-Host "        no existe en la linea base (archivo nuevo)" -ForegroundColor DarkGray
            } elseif (-not (Test-Path -LiteralPath $b)) {
                Write-Host "        no existe en la captura actual (archivo borrado o renombrado)" -ForegroundColor DarkGray
            } else {
                Mostrar-Diferencia $a $b
            }
        }
    }

    Write-Host ""
    Write-Host "================ RESUMEN ================" -ForegroundColor Cyan
    Write-Host ("  Programas comparados:      " + $nombres.Count)
    Write-Host ("  Programas con cambios:     " + $archivosConCambios)
    Write-Host ("  Cambios en OPTIMIZADO:     " + $cambios["optimizado"])
    Write-Host ("  Cambios en METRICAS:       " + $cambios["metricas"])
    Write-Host ("  Cambios en BITACORA:       " + $cambios["bitacora"])
    if ($archivosConCambios -eq 0) {
        Write-Host "  CERO diferencias contra la linea base." -ForegroundColor Green
    } else {
        Write-Host "  Hay diferencias: revisa si cada una era la esperada." -ForegroundColor Yellow
    }

    if ($archivosConCambios -gt 0 -or $fallos) { exit 1 }
    exit 0
} finally {
    Set-Location $ubicacionOriginal
}
