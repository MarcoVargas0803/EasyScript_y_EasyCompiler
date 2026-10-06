# pruebas_u2.ps1 - Arnes de regresion de la Unidad 2 (codigo intermedio).
#
# Captura, por cada *.txt del proyecto, tres cosas que no deben cambiar sin
# querer mientras se modifica el generador de codigo:
#
#   <base>.consola.txt     lo que imprime  java Main <archivo>  (sin banderas)
#   <base>.cuadruplos.txt  el codigo de tres direcciones generado
#   <base>.traza.txt       lo que HACE ese codigo al ejecutarlo en el simulador
#
# Las dos ultimas las produce VerificadorIntermedio. La traza no depende de los
# nombres de temporales ni de etiquetas: si una etapa renumera o fusiona saltos,
# los cuadruplos cambian (es lo esperado) pero la traza debe quedar igual.
#
# Uso:
#   .\pruebas_u2.ps1              Crea la linea base en linea_base_u2\
#                                 (se niega si ya existe, para no pisarla)
#   .\pruebas_u2.ps1 -Forzar      Rehace la linea base aunque ya exista
#   .\pruebas_u2.ps1 -Comparar    Captura en actual_u2\ y compara contra la
#                                 linea base, archivo por archivo
#
# Siempre compila primero con .\build.ps1, para que lo medido corresponda al
# codigo actual. Usa rutas completas al JDK y NO modifica $env:Path.
#
# Codigo de salida: 0 si todo bien; 1 si falla la compilacion, la linea base ya
# existe (sin -Forzar), hay saltos rotos o -Comparar encuentra diferencias.

param(
    [switch] $Comparar,
    [switch] $Forzar
)

$ErrorActionPreference = "Stop"

# JDK: la misma busqueda que build.ps1. JAVA_HOME gana si esta definido.
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
    Write-Host "No se encontro ningun JDK. Define JAVA_HOME o edita `$JDKS al inicio de pruebas_u2.ps1." -ForegroundColor Red
    exit 1
}

$proyecto  = $PSScriptRoot
$dirBase   = Join-Path $proyecto "linea_base_u2"
$dirActual = Join-Path $proyecto "actual_u2"

# ---------------------------------------------------------------------------
# Ejecuta java y guarda su salida TAL CUAL, byte por byte.
#
# No se usa "> archivo" porque PowerShell decodifica y vuelve a codificar la
# salida de los programas externos (y lo hace distinto en 5.1 y en 7), lo que
# alteraria los acentos, los emojis y los codigos ANSI. Leyendo el flujo crudo
# del proceso la captura es identica a lo que Java escribio.
# ---------------------------------------------------------------------------
function Guardar-SalidaJava([string] $argumentos, [string] $destino) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $java
    $psi.Arguments = $argumentos
    $psi.WorkingDirectory = $proyecto
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    # stderr se lee en paralelo: si se llenara su buffer mientras se espera
    # stdout, el proceso se quedaria bloqueado.
    $err = $p.StandardError.ReadToEndAsync()
    $fs = [System.IO.File]::Create($destino)
    try {
        $p.StandardOutput.BaseStream.CopyTo($fs)
        $p.WaitForExit()
        # Si hubo algo en stderr (una excepcion de Java, por ejemplo) se
        # agrega al final: tambien es parte de lo que vio la consola.
        $textoErr = $err.Result
        if ($textoErr.Length -gt 0) {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($textoErr)
            $fs.Write($bytes, 0, $bytes.Length)
        }
    } finally {
        $fs.Close()
    }
    return $p.ExitCode
}

# Captura completa (consola + cuadruplos + traza) en el directorio indicado.
# Devuelve el codigo de salida de VerificadorIntermedio.
function Capturar([string] $dir) {
    New-Item -ItemType Directory -Force $dir | Out-Null

    $archivos = Get-ChildItem -Path $proyecto -Filter *.txt -File | Sort-Object Name
    Write-Host ("Capturando la consola de Main para " + $archivos.Count + " archivos...") -ForegroundColor Cyan
    foreach ($f in $archivos) {
        $destino = Join-Path $dir ($f.BaseName + ".consola.txt")
        Guardar-SalidaJava ('-Dfile.encoding=UTF-8 -cp out Main "' + $f.Name + '"') $destino | Out-Null
    }

    Write-Host "Ejecutando VerificadorIntermedio..." -ForegroundColor Cyan
    # Out-Host: la tabla va a la pantalla y no al valor de retorno de la funcion.
    & $java "-Dfile.encoding=UTF-8" -cp out VerificadorIntermedio -salida $dir | Out-Host
    return $LASTEXITCODE
}

# Las diferencias se muestran con el caracter ESC visible: impreso tal cual,
# un codigo ANSI cambiaria el color de la terminal en vez de verse.
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
    if ($primera -ge 0) {
        Write-Host ("        primera linea distinta: " + ($primera + 1) +
                    "  (linea base: " + $la.Length + " lineas, actual: " + $lb.Length + ")") -ForegroundColor DarkGray
    } else {
        Write-Host "        las lineas coinciden: la diferencia esta en los fines de linea o en bytes invisibles" -ForegroundColor DarkGray
        return
    }

    # Las primeras lineas distintas, en orden y por parejas: asi se ve que
    # cambio en cada sitio. Compare-Object agrupa por lado y pierde ese orden.
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

    # Total aproximado de lineas que difieren, sin importar su posicion.
    $total = @(Compare-Object -ReferenceObject $la -DifferenceObject $lb -CaseSensitive).Count
    Write-Host ("        (" + $total + " lineas aparecen solo en uno de los dos lados, segun Compare-Object)") -ForegroundColor DarkGray
}

# ---------------------------------------------------------------------------
# 1. Compilar
# ---------------------------------------------------------------------------
$ubicacionOriginal = Get-Location
try {
    Set-Location $proyecto

    if (-not $Comparar -and (Test-Path $dirBase) -and -not $Forzar) {
        Write-Host "La linea base ya existe en linea_base_u2\ y no se va a sobrescribir." -ForegroundColor Yellow
        Write-Host "  Para comparar contra ella:      .\pruebas_u2.ps1 -Comparar" -ForegroundColor Yellow
        Write-Host "  Para rehacerla (se pierde):     .\pruebas_u2.ps1 -Forzar" -ForegroundColor Yellow
        exit 1
    }
    if ($Comparar -and -not (Test-Path $dirBase)) {
        Write-Host "No hay linea base que comparar. Creala primero con .\pruebas_u2.ps1" -ForegroundColor Red
        exit 1
    }

    Write-Host "== Compilando con build.ps1 ==" -ForegroundColor Cyan
    try {
        & (Join-Path $proyecto "build.ps1")
    } catch {
        Write-Host ("La compilacion fallo; no se mide nada. " + $_) -ForegroundColor Red
        exit 1
    }
    Set-Location $proyecto

    # -----------------------------------------------------------------------
    # 2. Capturar
    # -----------------------------------------------------------------------
    $dir = if ($Comparar) { $dirActual } else { $dirBase }
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }

    Write-Host ""
    Write-Host ("== Capturando en " + (Split-Path $dir -Leaf) + "\ ==") -ForegroundColor Cyan
    $codigoVerificador = Capturar $dir
    $saltosRotos = ($codigoVerificador -ne 0)
    if ($saltosRotos) {
        Write-Host "ATENCION: VerificadorIntermedio encontro saltos rotos o pendientes (ver tabla)." -ForegroundColor Red
    }

    if (-not $Comparar) {
        Write-Host ""
        Write-Host "Linea base creada en linea_base_u2\" -ForegroundColor Green
        if ($saltosRotos) { exit 1 }
        exit 0
    }

    # -----------------------------------------------------------------------
    # 3. Comparar contra la linea base
    # -----------------------------------------------------------------------
    Write-Host ""
    Write-Host "== Comparando actual_u2\ contra linea_base_u2\ ==" -ForegroundColor Cyan

    # Programas de los dos lados: los que hay ahora y los que habia en la
    # linea base (por si se borro o renombro alguno).
    $nombres = @{}
    foreach ($f in (Get-ChildItem -Path $proyecto -Filter *.txt -File)) { $nombres[$f.BaseName] = $true }
    foreach ($f in (Get-ChildItem -Path $dirBase -Filter *.consola.txt -File)) {
        $nombres[$f.Name.Substring(0, $f.Name.Length - ".consola.txt".Length)] = $true
    }

    $tipos = @("consola", "cuadruplos", "traza")
    $cambios = @{ consola = 0; cuadruplos = 0; traza = 0 }
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
            Write-Host ("  [igual]   " + $base + ".txt") -ForegroundColor DarkGray
            continue
        }

        $archivosConCambios++
        Write-Host ("  [CAMBIO]  " + $base + ".txt  ->  " + ($distintos -join ", ")) -ForegroundColor Yellow
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
    Write-Host ("  Cambios en CONSOLA:        " + $cambios["consola"])
    Write-Host ("  Cambios en CUADRUPLOS:     " + $cambios["cuadruplos"])
    Write-Host ("  Cambios en TRAZA:          " + $cambios["traza"])
    if ($archivosConCambios -eq 0) {
        Write-Host "  CERO diferencias contra la linea base." -ForegroundColor Green
    } else {
        Write-Host "  Hay diferencias: revisa si cada una era la esperada." -ForegroundColor Yellow
    }

    if ($archivosConCambios -gt 0 -or $saltosRotos) { exit 1 }
    exit 0
} finally {
    Set-Location $ubicacionOriginal
}
