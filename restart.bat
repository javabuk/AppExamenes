@echo off
setlocal
cd /d "%~dp0"
title FHIR Cert Master - Reiniciar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0restart.ps1" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Pulse cualquier tecla para cerrar esta ventana...
    pause >nul
)
