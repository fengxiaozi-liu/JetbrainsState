@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0configure-exe.ps1" %*
set "setupExitCode=%ERRORLEVEL%"
if not "%setupExitCode%"=="0" echo Stopped. Review the error above. No success is implied.
pause
exit /b %setupExitCode%
