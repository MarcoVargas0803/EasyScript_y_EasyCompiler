@echo off
rem easy.cmd - Comando "easy" de EasyCompiler. Es lo que encuentra el PATH; la
rem logica vive en lanzador.ps1, junto a este archivo (%~dp0). El Bypass aplica solo a
rem este proceso: no cambia la politica de ejecucion del equipo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0lanzador.ps1" %*
exit /b %ERRORLEVEL%
