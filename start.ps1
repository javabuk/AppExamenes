<#
.SYNOPSIS
    Inicia la aplicación FHIR Cert Master en Windows 11.
.DESCRIPTION
    Comprueba si la aplicación ya está arrancada en el puerto configurado.
    Permite configurar el puerto mediante 'app.conf' o mediante parámetro -Port.
    Verifica que el puerto esté libre y arranca el servicio en segundo plano,
    abriendo opcionalmente el navegador una vez el servidor responde.
.PARAMETER Port
    Puerto opcional en el que arrancar (anula el valor de app.conf).
.PARAMETER NoBrowser
    Si se especifica, no abre el navegador automáticamente tras arrancar.
.PARAMETER Foreground
    Si se especifica, arranca en la consola actual para ver los logs en directo.
.EXAMPLE
    .\start.ps1
    .\start.ps1 -Port 9090
    .\start.ps1 -Port 8081 -NoBrowser
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [int]$Port = 0,
    [switch]$NoBrowser,
    [switch]$Foreground
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# 1. Cargar configuración desde app.conf
$ConfigFile = Join-Path $ScriptDir "app.conf"
$ConfigPort = 8080
$ConfigOpenBrowser = $true
$ConfigJvmMemory = "1024m"

if (Test-Path $ConfigFile) {
    Get-Content $ConfigFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim().ToUpper()
            $val = $parts[1].Trim()
            switch ($key) {
                "PORT" {
                    $parsed = 0
                    if ([int]::TryParse($val, [ref]$parsed) -and $parsed -gt 0) {
                        $ConfigPort = $parsed
                    }
                }
                "OPEN_BROWSER" {
                    $ConfigOpenBrowser = ($val.ToLower() -eq "true" -or $val -eq "1")
                }
                "JVM_MAX_MEMORY" {
                    if ($val) { $ConfigJvmMemory = $val }
                }
            }
        }
    }
}

# El parámetro explícito tiene prioridad sobre el fichero de configuración
$EffectivePort = if ($Port -gt 0) { $Port } else { $ConfigPort }
$EffectiveOpenBrowser = if ($NoBrowser) { $false } else { $ConfigOpenBrowser }

$PidFile = Join-Path $ScriptDir "app.pid"
$PortFile = Join-Path $ScriptDir "app.port"

# 2. Comprobar si la aplicación YA está en ejecución según app.pid
if (Test-Path $PidFile) {
    $savedPidStr = (Get-Content $PidFile -ErrorAction SilentlyContinue | Out-String).Trim()
    $savedPid = 0
    if ([int]::TryParse($savedPidStr, [ref]$savedPid) -and $savedPid -gt 0) {
        $p = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
        if ($p -and ($p.ProcessName -eq "java" -or $p.ProcessName -eq "javaw")) {
            $runningPort = $EffectivePort
            if (Test-Path $PortFile) {
                $pVal = (Get-Content $PortFile -ErrorAction SilentlyContinue | Out-String).Trim()
                if ($pVal) { $runningPort = $pVal }
            }
            Write-Host ""
            Write-Host "============================================================" -ForegroundColor Yellow
            Write-Host " [AVISO] FHIR Cert Master YA ESTA EN EJECUCION" -ForegroundColor Yellow
            Write-Host "============================================================" -ForegroundColor Yellow
            Write-Host " * Estado:   Activo y respondiendo peticiones" -ForegroundColor Green
            Write-Host " * PID:      $savedPid ($($p.ProcessName))" -ForegroundColor Cyan
            Write-Host " * Puerto:   $runningPort" -ForegroundColor Cyan
            Write-Host " * URL:      http://localhost:$runningPort" -ForegroundColor Green
            Write-Host ""
            Write-Host " Para detener la aplicacion: .\stop.ps1 (o doble clic en stop.bat)" -ForegroundColor Gray
            Write-Host "============================================================" -ForegroundColor Yellow
            Write-Host ""

            if ($EffectiveOpenBrowser) {
                Start-Process "http://localhost:$runningPort"
            }
            exit 0
        }
    }
    # Si el PID ya no existe en el sistema, limpiar archivo huérfano
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    Remove-Item $PortFile -Force -ErrorAction SilentlyContinue
}

# 3. Comprobar si el puerto está en uso por otra instancia o proceso
$existingConn = Get-NetTCPConnection -LocalPort $EffectivePort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1

if ($existingConn) {
    $existingPid = $existingConn.OwningProcess
    $existingProc = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
    $procName = if ($existingProc) { $existingProc.ProcessName } else { "Proceso desconocido" }

    if ($procName -eq "java" -or $procName -eq "javaw") {
        # Guardar tracking files para consistencia
        Set-Content -Path $PidFile -Value $existingPid -Force
        Set-Content -Path $PortFile -Value $EffectivePort -Force

        Write-Host ""
        Write-Host "============================================================" -ForegroundColor Yellow
        Write-Host " [AVISO] FHIR Cert Master YA ESTA EN EJECUCION" -ForegroundColor Yellow
        Write-Host "============================================================" -ForegroundColor Yellow
        Write-Host " * Estado:   Activo y escuchando en puerto $EffectivePort" -ForegroundColor Green
        Write-Host " * PID:      $existingPid ($procName)" -ForegroundColor Cyan
        Write-Host " * URL:      http://localhost:$EffectivePort" -ForegroundColor Green
        Write-Host ""
        Write-Host " Para detener la aplicacion: .\stop.ps1 (o doble clic en stop.bat)" -ForegroundColor Gray
        Write-Host "============================================================" -ForegroundColor Yellow
        Write-Host ""

        if ($EffectiveOpenBrowser) {
            Start-Process "http://localhost:$EffectivePort"
        }
        exit 0
    } else {
        Write-Host ""
        Write-Host "============================================================" -ForegroundColor Red
        Write-Host " [ERROR] El puerto $EffectivePort ya esta ocupado por otro proceso" -ForegroundColor Red
        Write-Host "============================================================" -ForegroundColor Red
        Write-Host " * PID:      $existingPid ($procName)" -ForegroundColor Red
        Write-Host " * Solucion: Cambia el puerto en 'app.conf' (ej: PORT=8081)" -ForegroundColor Yellow
        Write-Host "             o ejecuta: .\start.ps1 -Port 8081" -ForegroundColor Yellow
        Write-Host "============================================================" -ForegroundColor Red
        Write-Host ""
        exit 1
    }
}

# 4. Validar existencia de Java
$javaCmd = Get-Command "java" -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "[ERROR] No se encontro Java en el PATH del sistema." -ForegroundColor Red
    Write-Host "Instala Java 21 o superior y asegurate de que 'java.exe' este en tu PATH." -ForegroundColor Yellow
    exit 1
}

# 5. Validar existencia del JAR compilado
$JarPath = (Resolve-Path (Join-Path $ScriptDir "target\fhir-exam-app-1.0.0.jar") -ErrorAction SilentlyContinue).Path
if (-not $JarPath -or -not (Test-Path $JarPath)) {
    Write-Host "[INFO] Binario compilado no encontrado en target\." -ForegroundColor Yellow
    Write-Host "Compilando con Maven..." -ForegroundColor Cyan
    $mvnCmd = Get-Command "mvn" -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        & mvn clean package -DskipTests
        $JarPath = (Resolve-Path (Join-Path $ScriptDir "target\fhir-exam-app-1.0.0.jar") -ErrorAction SilentlyContinue).Path
        if (-not $JarPath -or -not (Test-Path $JarPath)) {
            Write-Host "[ERROR] La compilacion con Maven fallo." -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "[ERROR] Se requiere el archivo target\fhir-exam-app-1.0.0.jar." -ForegroundColor Red
        Write-Host "Ejecuta 'mvn package -DskipTests' antes de arrancar." -ForegroundColor Yellow
        exit 1
    }
}

# 6. Preparar directorio de logs
$LogsDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
}
$StdoutLog = Join-Path $LogsDir "app.log"
$StderrLog = Join-Path $LogsDir "app-error.log"

Remove-Item env:SPRING_CONFIG_LOCATION -ErrorAction SilentlyContinue

# 7. Modo Primer Plano (Foreground)
if ($Foreground) {
    Write-Host "Iniciando FHIR Cert Master en primer plano en puerto $EffectivePort..." -ForegroundColor Green
    & java "-Xmx$ConfigJvmMemory" -jar "$JarPath" "--server.port=$EffectivePort"
    exit 0
}

# 8. Modo Segundo Plano (Background / Daemon) totalmente desacoplado mediante WMI
$javaBinary = if (Get-Command "javaw" -ErrorAction SilentlyContinue) { (Get-Command "javaw").Source } else { (Get-Command "java").Source }

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " INICIANDO FHIR CERT MASTER (Windows 11)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " * Puerto configurado: $EffectivePort" -ForegroundColor White
Write-Host " * Memoria JVM:        $ConfigJvmMemory" -ForegroundColor White
Write-Host " * Lanzador:           $javaBinary" -ForegroundColor Gray
Write-Host " * Registro de logs:   $StdoutLog" -ForegroundColor Gray
Write-Host ""
Write-Host "Arrancando servicio en segundo plano..." -ForegroundColor Yellow

$launchCmd = "`"$javaBinary`" -Xmx$ConfigJvmMemory -jar `"$JarPath`" --server.port=$EffectivePort"
$wmiCmd = "cmd.exe /c `"$launchCmd > `"$StdoutLog`" 2> `"$StderrLog`"`""

$res = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $wmiCmd; CurrentDirectory = $ScriptDir }
$launcherPid = $res.ProcessId

Set-Content -Path $PortFile -Value $EffectivePort -Force

# 9. Esperar a que el servidor este listo y capturar el PID real de Java
$timeoutSec = 35
$startTime = Get-Date
$isReady = $false
$realPid = $launcherPid

Write-Host -NoNewline "Esperando respuesta del servidor"
while ((Get-Date) -lt $startTime.AddSeconds($timeoutSec)) {
    $listening = Get-NetTCPConnection -LocalPort $EffectivePort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listening) {
        $realPid = $listening.OwningProcess
        Set-Content -Path $PidFile -Value $realPid -Force

        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:$EffectivePort/api/topics" -TimeoutSec 2 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($resp -and $resp.StatusCode -eq 200) {
                $isReady = $true
                break
            }
        } catch {}
    }

    Write-Host -NoNewline "." -ForegroundColor Cyan
    Start-Sleep -Milliseconds 700
}
Write-Host ""

if ($isReady) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " ¡APLICACION ARRANCADA CON EXITO!" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " * PID:         $realPid" -ForegroundColor White
    Write-Host " * Puerto:      $EffectivePort" -ForegroundColor White
    Write-Host " * URL Local:   http://localhost:$EffectivePort" -ForegroundColor Green
    Write-Host " * Archivo log: $StdoutLog" -ForegroundColor Gray
    Write-Host ""
    Write-Host " Acciones disponibles:" -ForegroundColor Gray
    Write-Host "   - Para detener:  .\stop.ps1   (o doble clic en stop.bat)" -ForegroundColor Gray
    Write-Host "   - Para estado:   .\status.ps1 (o doble clic en status.bat)" -ForegroundColor Gray
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""

    if ($EffectiveOpenBrowser) {
        Start-Process "http://localhost:$EffectivePort"
    }
} else {
    Write-Host ""
    Write-Host "[AVISO] El proceso sigue ejecutandose (PID: $realPid), pero no respondio dentro de $timeoutSec s." -ForegroundColor Yellow
    Write-Host "Puedes consultar el log en directo con: Get-Content -Wait $StdoutLog" -ForegroundColor Yellow
}
