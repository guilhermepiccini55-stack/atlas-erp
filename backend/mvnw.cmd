@echo off
rem Maven Wrapper (Windows): delega para um script PowerShell que baixa e executa o Maven.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0.mvn\wrapper\mvnw.ps1" %*
exit /b %ERRORLEVEL%
