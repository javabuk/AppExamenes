@echo off
setlocal
cd /d "%~dp0"
title FHIR Cert Master - Iniciar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Pulse cualquier tecla para cerrar esta ventana...
    pause >nul
)
