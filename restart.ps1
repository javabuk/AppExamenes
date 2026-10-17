<#
.SYNOPSIS
    Reinicia la aplicación FHIR Cert Master en Windows 11.
.DESCRIPTION
    Ejecuta una parada limpia y vuelve a arrancar la aplicación en el puerto configurado.
.PARAMETER Port
    Puerto opcional en el que reiniciar.
.EXAMPLE
    .\restart.ps1
    .\restart.ps1 -Port 9090
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [int]$Port = 0
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "Reiniciando FHIR Cert Master..." -ForegroundColor Cyan

if ($Port -gt 0) {
    & (Join-Path $ScriptDir "stop.ps1") -Port $Port
    Start-Sleep -Seconds 1
    & (Join-Path $ScriptDir "start.ps1") -Port $Port
} else {
    & (Join-Path $ScriptDir "stop.ps1")
    Start-Sleep -Seconds 1
    & (Join-Path $ScriptDir "start.ps1")
}
