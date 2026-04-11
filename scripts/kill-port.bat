@echo off
set PORT=%1
if "%PORT%"=="" (
    echo Usage: kill-port.bat ^<port^>
    exit /b 1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT% ^| findstr LISTENING') do (
    echo Found process on port %PORT%: PID %%a
    set PID=%%a
)

if defined PID (
    echo Killing PID %PID%...
    taskkill //F //PID %PID%
) else (
    echo No process found on port %PORT%
)
