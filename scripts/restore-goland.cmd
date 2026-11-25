@echo off
call "%~dp0run-config.cmd" -Product GoLand -Mode Restore
exit /b %ERRORLEVEL%
