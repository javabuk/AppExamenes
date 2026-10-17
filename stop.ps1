<#
.SYNOPSIS
    Detiene la aplicación FHIR Cert Master en Windows 11.
.DESCRIPTION
    Identifica y finaliza el proceso de la aplicación según app.pid y el puerto configurado,
    verificando que no afecte a otros procesos de Java del sistema (como servidores de VS Code).
.PARAMETER Port
    Puerto opcional del que detener la aplicación (por defecto lee app.port o app.conf).
.PARAMETER Force
    Fuerza el cierre inmediato.
.EXAMPLE
    .\stop.ps1
    .\stop.ps1 -Port 9090
    .\stop.ps1 -Force
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [int]$Port = 0,
    [switch]$Force
)

$ErrorActionPreference = "SilentlyContinue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

$ConfigFile = Join-Path $ScriptDir "app.conf"
$PidFile = Join-Path $ScriptDir "app.pid"
$PortFile = Join-Path $ScriptDir "app.port"

# 1. Determinar puerto objetivo
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
Write-Host " DETENIENDO FHIR CERT MASTER (Puerto $TargetPort)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$stoppedAny = $false

# 2. Detener proceso registrado en app.pid si existe
if (Test-Path $PidFile) {
    $savedPidStr = (Get-Content $PidFile | Out-String).Trim()
    $savedPid = 0
    if ([int]::TryParse($savedPidStr, [ref]$savedPid) -and $savedPid -gt 0) {
        $proc = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "* Deteniendo proceso registrado PID $savedPid ($($proc.ProcessName))..." -ForegroundColor Yellow
            Stop-Process -Id $savedPid -Force -ErrorAction SilentlyContinue
            $stoppedAny = $true
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

# 3. Detener cualquier proceso en el puerto objetivo
$connections = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue
foreach ($conn in $connections) {
    $pId = $conn.OwningProcess
    if ($pId -gt 0) {
        $proc = Get-Process -Id $pId -ErrorAction SilentlyContinue
        if ($proc) {
            Write-Host "* Deteniendo proceso en puerto $TargetPort (PID: $pId, $($proc.ProcessName))..." -ForegroundColor Yellow
            Stop-Process -Id $pId -Force -ErrorAction SilentlyContinue
            $stoppedAny = $true
        }
    }
}

# 4. Detener cualquier proceso específico de fhir-exam-app
$allOurProcs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { 
    ($_.Name -eq "java.exe" -or $_.Name -eq "javaw.exe") -and $_.CommandLine -match "fhir-exam-app" 
}
foreach ($p in $allOurProcs) {
    Write-Host "* Deteniendo proceso fhir-exam-app PID $($p.ProcessId)..." -ForegroundColor Yellow
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    $stoppedAny = $true
}

# Limpiar archivos de seguimiento
Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
Remove-Item $PortFile -Force -ErrorAction SilentlyContinue

# 5. Verificar que el puerto ha quedado liberado
Start-Sleep -Milliseconds 800
$stillListening = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue

if ($stillListening) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host " [ERROR] El puerto $TargetPort todavia no se ha liberado por completo." -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host ""
    exit 1
} else {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    if ($stoppedAny) {
        Write-Host " [OK] Aplicacion detenida correctamente." -ForegroundColor Green
        Write-Host " Puerto $TargetPort liberado y listo para nuevo uso." -ForegroundColor Green
    } else {
        Write-Host " [INFO] La aplicacion no estaba en ejecucion (puerto $TargetPort libre)." -ForegroundColor Yellow
    }
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
    exit 0
}
