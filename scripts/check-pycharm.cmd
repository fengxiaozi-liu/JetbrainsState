@echo off
call "%~dp0run-config.cmd" -Product PyCharm -Mode Check
exit /b %ERRORLEVEL%
