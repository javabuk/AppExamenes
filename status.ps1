<#
.SYNOPSIS
    Muestra el estado actual de FHIR Cert Master en Windows 11.
.DESCRIPTION
    Comprueba si el proceso está activo en el puerto configurado o guardado,
    mostrando el PID, uso de memoria RAM, tiempo de actividad y enlace web.
.PARAMETER Port
    Puerto opcional que comprobar (por defecto lee app.port o app.conf).
.EXAMPLE
    .\status.ps1
    .\status.ps1 -Port 9090
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [int]$Port = 0
)

$ErrorActionPreference = "SilentlyContinue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

$ConfigFile = Join-Path $ScriptDir "app.conf"
$PidFile = Join-Path $ScriptDir "app.pid"
$PortFile = Join-Path $ScriptDir "app.port"

# 1. Determinar puerto
$TargetPort = 0
if ($Port -gt 0) {
    $TargetPort = $Port
} elseif (Test-Path $PortFile) {
    $savedPort = (Get-Content $PortFile | Out-String).Trim()
    [int]::TryParse($savedPort, [ref]$TargetPort) | Out-Null
}

if ($TargetPort -le 0 -and (Test-Path $ConfigFile)) {
    Get-Content $ConfigFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line.ToUpper().StartsWith("PORT=")) {
            $val = $line.Substring(5).Trim()
            [int]::TryParse($val, [ref]$TargetPort) | Out-Null
        }
    }
}

if ($TargetPort -le 0) {
    $TargetPort = 8080
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " ESTADO DE FHIR CERT MASTER" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$conn = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1

if ($conn) {
    $pId = $conn.OwningProcess
    $proc = Get-Process -Id $pId -ErrorAction SilentlyContinue
    $memMB = if ($proc) { [math]::Round($proc.WorkingSet64 / 1MB, 1) } else { 0 }
    $startTime = if ($proc) { $proc.StartTime.ToString("yyyy-MM-dd HH:mm:ss") } else { "-" }

    Write-Host " • Estado:        " -NoNewline
    Write-Host "EN EJECUCIÓN (Activo)" -ForegroundColor Green
    Write-Host " • Puerto:        $TargetPort" -ForegroundColor White
    Write-Host " • PID:           $pId" -ForegroundColor White
    Write-Host " • Memoria RAM:   $memMB MB" -ForegroundColor White
    Write-Host " • Iniciado el:   $startTime" -ForegroundColor White
    Write-Host " • URL Local:     http://localhost:$TargetPort" -ForegroundColor Green
    Write-Host ""
    Write-Host " Para detener la aplicación: .\stop.ps1 (o doble clic en stop.bat)" -ForegroundColor Gray
    Write-Host " Para reiniciar:            .\restart.ps1 (o doble clic en restart.bat)" -ForegroundColor Gray
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
} else {
    Write-Host " • Estado:        " -NoNewline
    Write-Host "DETENIDO (Inactivo)" -ForegroundColor Red
    Write-Host " • Puerto:        $TargetPort (Libre)" -ForegroundColor Gray
    Write-Host ""
    Write-Host " Para arrancar la aplicación: .\start.ps1 (o doble clic en start.bat)" -ForegroundColor Gray
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
}
