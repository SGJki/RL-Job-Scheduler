# Start Infrastructure Services
# Usage: .\start-infra.ps1 [-All] [-MySQL] [-Redis] [-Nacos]

param(
    [switch]$All,
    [switch]$MySQL,
    [switch]$Redis,
    [switch]$Nacos
)

$ErrorActionPreference = "Continue"

# Service paths
$NACOS_BIN = "C:\Users\13253\softwareDisk\tool\nacos\bin\startup.cmd"
$MYSQL_CMD = "sudo net start mysql"
$REDIS_CMD = "redis-server"

# Colors
function Write-Banner($text) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " $text" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

# Start MySQL
function Start-MySQL {
    Write-Banner "Starting MySQL"
    try {
        $service = Get-Service -Name MySQL -ErrorAction SilentlyContinue
        if ($service) {
            if ($service.Status -eq "Running") {
                Write-Host "MySQL is already running" -ForegroundColor Yellow
            } else {
                Start-Service MySQL
                Write-Host "MySQL started successfully" -ForegroundColor Green
            }
        } else {
            Write-Host "MySQL service not found, trying to start..." -ForegroundColor Yellow
            Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "$MYSQL_CMD" -NoNewWindow
            Start-Sleep -Seconds 2
            Write-Host "MySQL start command sent" -ForegroundColor Green
        }
    } catch {
        Write-Host "Failed to start MySQL: $_" -ForegroundColor Red
    }
}

# Start Redis
function Start-Redis {
    Write-Banner "Starting Redis"
    try {
        $process = Get-Process redis-server -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "Redis is already running (PID: $($process.Id))" -ForegroundColor Yellow
        } else {
            Start-Process -FilePath "redis-server" -NoNewWindow
            Start-Sleep -Seconds 2
            $newProcess = Get-Process redis-server -ErrorAction SilentlyContinue
            if ($newProcess) {
                Write-Host "Redis started successfully (PID: $($newProcess.Id))" -ForegroundColor Green
            } else {
                Write-Host "Redis may have started, please verify manually" -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host "Failed to start Redis: $_" -ForegroundColor Red
    }
}

# Start Nacos
function Start-Nacos {
    Write-Banner "Starting Nacos (standalone mode)"
    try {
        $process = Get-Process -Name "startup" -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*nacos*" }
        if ($process) {
            Write-Host "Nacos appears to be running" -ForegroundColor Yellow
        } else {
            if (Test-Path $NACOS_BIN) {
                Write-Host "Executing: $NACOS_BIN -m standalone" -ForegroundColor Gray
                Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "cd /d C:\Users\13253\softwareDisk\tool\nacos\bin && startup.cmd -m standalone" -NoNewWindow
                Write-Host "Nacos starting in standalone mode..." -ForegroundColor Green
                Write-Host "Wait ~30s for Nacos console at http://localhost:8848/nacos" -ForegroundColor Cyan
            } else {
                Write-Host "Nacos startup script not found at: $NACOS_BIN" -ForegroundColor Red
            }
        }
    } catch {
        Write-Host "Failed to start Nacos: $_" -ForegroundColor Red
    }
}

# Main
if (-not ($MySQL -or $Redis -or $Nacos -or $All)) {
    Write-Host ""
    Write-Host "Start Infrastructure Services" -ForegroundColor Cyan
    Write-Host "Usage: .\start-infra.ps1 [-All] [-MySQL] [-Redis] [-Nacos]" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Yellow
    Write-Host "  .\start-infra.ps1 -All       # Start all services"
    Write-Host "  .\start-infra.ps1 -Nacos     # Start Nacos only"
    Write-Host "  .\start-infra.ps1 -MySQL -Redis   # Start MySQL and Redis"
    Write-Host ""
    exit 0
}

if ($All -or $MySQL) { Start-MySQL }
if ($All -or $Redis) { Start-Redis }
if ($All -or $Nacos) { Start-Nacos }

Write-Host ""
Write-Host "Done!" -ForegroundColor Green
