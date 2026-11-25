@echo off
call "%~dp0run-config.cmd" -Product GoLand -Mode Check
exit /b %ERRORLEVEL%
