@echo off
setlocal
cd /d "%~dp0"
title FHIR Cert Master - Estado
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0status.ps1" %*
echo.
echo Pulse cualquier tecla para cerrar esta ventana...
pause >nul
