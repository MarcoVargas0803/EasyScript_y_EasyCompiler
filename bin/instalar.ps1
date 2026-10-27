# instalar.ps1 - Agrega el comando "easy" al PATH del usuario.
#
# Uso (una sola vez, desde cualquier carpeta):
#   powershell -ExecutionPolicy Bypass -File bin\instalar.ps1
#   powershell -ExecutionPolicy Bypass -File bin\instalar.ps1 -Desinstalar
#
# Que hace:
#   1. Lee el Path del USUARIO (HKCU\Environment) tal cual esta guardado, sin
#      expandir: asi %JAVA_HOME%\bin se conserva como referencia y no se
#      convierte en una ruta fija.
#   2. Guarda un respaldo del valor y de su tipo en bin\respaldo_path_<fecha>.txt.
#   3. Agrega la carpeta bin\ al final (o la quita con -Desinstalar). Si ya
#      estaba, no la duplica.
#   4. Lo escribe como REG_EXPAND_SZ. Si estaba como REG_SZ (texto simple),
#      Windows nunca expandia %JAVA_HOME% y por eso "java" no se encontraba.
#   5. Avisa a Windows del cambio para que las terminales NUEVAS lo vean. Las que
#      ya estan abiertas (y VS Code completo) hay que cerrarlas y abrirlas.
#
# Solo toca el PATH del usuario: el del sistema no se modifica y no hace falta
# ser administrador. No usa setx, que recorta el valor a 1024 caracteres.

param(
    [switch] $Desinstalar
)

$ErrorActionPreference = "Stop"

$carpeta = $PSScriptRoot.TrimEnd('\')

$clave = [Microsoft.Win32.Registry]::CurrentUser.OpenSubKey("Environment", $true)
$opciones = [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames
$actual = $clave.GetValue("Path", "", $opciones)
$tipo = if ($clave.GetValueNames() -contains "Path") { $clave.GetValueKind("Path") } else { "(no existia)" }

# Respaldo antes de tocar nada.
$respaldo = Join-Path $carpeta ("respaldo_path_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".txt")
Set-Content -LiteralPath $respaldo -Value @("Tipo: $tipo", "Valor:", $actual)
Write-Host "Respaldo del PATH de usuario: $respaldo" -ForegroundColor DarkGray

# Se compara sin distinguir mayusculas ni la diagonal final.
$entradas = @($actual -split ";" | Where-Object { $_ -ne "" })
$otras = @($entradas | Where-Object { $_.TrimEnd('\') -ine $carpeta })
$yaEstaba = ($otras.Count -ne $entradas.Count)

if ($Desinstalar) {
    $nuevas = $otras
} else {
    $nuevas = $otras + $carpeta
}
$nuevo = $nuevas -join ";"

$clave.SetValue("Path", $nuevo, [Microsoft.Win32.RegistryValueKind]::ExpandString)
$clave.Close()

# WM_SETTINGCHANGE: sin este aviso, Explorer (y lo que se abra desde el) seguiria
# usando el PATH viejo hasta cerrar sesion.
Add-Type -Namespace EasyCompiler -Name Win32 -MemberDefinition @"
[DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Auto)]
public static extern System.IntPtr SendMessageTimeout(System.IntPtr hWnd, uint Msg,
    System.UIntPtr wParam, string lParam, uint fuFlags, uint uTimeout, out System.UIntPtr lpdwResult);
"@
$resultado = [System.UIntPtr]::Zero
[EasyCompiler.Win32]::SendMessageTimeout([System.IntPtr]0xffff, 0x1A, [System.UIntPtr]::Zero,
    "Environment", 2, 5000, [ref] $resultado) | Out-Null

if ($Desinstalar) {
    if ($yaEstaba) { Write-Host "Se quito $carpeta del PATH de usuario." -ForegroundColor Green }
    else { Write-Host "$carpeta no estaba en el PATH de usuario; no habia nada que quitar." -ForegroundColor Yellow }
} else {
    if ($yaEstaba) { Write-Host "$carpeta ya estaba en el PATH de usuario." -ForegroundColor Yellow }
    else { Write-Host "Se agrego $carpeta al PATH de usuario." -ForegroundColor Green }
}
if ($tipo -ne "ExpandString") {
    Write-Host "El PATH de usuario estaba como $tipo; ahora es REG_EXPAND_SZ (%JAVA_HOME% ya se expande)." -ForegroundColor Green
}
Write-Host "Abre una terminal NUEVA (en VS Code: cierralo por completo y vuelve a abrirlo) y prueba:" -ForegroundColor Cyan
Write-Host "  easy Tests\codigo1.easy" -ForegroundColor Cyan
