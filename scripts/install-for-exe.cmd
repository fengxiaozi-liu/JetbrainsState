@echo off
setlocal
echo Installing the Agent under the GoLand installation directory requires write access.
echo If access is denied, run this file as administrator using the same Windows account.
powershell.exe -NoProfile -File "%~dp0configure-exe.ps1" -Mode Install
set "setupExitCode=%ERRORLEVEL%"
if not "%setupExitCode%"=="0" echo Installation stopped. Review the error above before retrying.
pause
exit /b %setupExitCode%
