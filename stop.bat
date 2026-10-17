@echo off
setlocal
cd /d "%~dp0"
title FHIR Cert Master - Detener
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1" %*
echo.
echo Pulse cualquier tecla para cerrar esta ventana...
pause >nul
