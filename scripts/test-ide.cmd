@echo off
powershell.exe -NoProfile -File "%~dp0launch-isolated.ps1" -ProjectPath "%~dp0..\smoke-project"
if errorlevel 1 pause
