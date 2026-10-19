# PowerShell script to start LedgerFlow local container infrastructure
Param(
    [string]$Profile = "core"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Starting LedgerFlow Infrastructure: $Profile" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

docker compose --profile $Profile up -d

Write-Host "`nWaiting for PostgreSQL on port 5433 to become ready..." -ForegroundColor Yellow
$retries = 30
while ($retries -gt 0) {
    $healthy = docker inspect --format='{{json .State.Health.Status}}' ledgerflow-postgres 2>$null
    if ($healthy -eq '"healthy"') {
        Write-Host "PostgreSQL is healthy on localhost:5433!" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 1
    $retries--
}

if ($retries -eq 0) {
    Write-Host "Warning: PostgreSQL health check timed out. Please check docker logs." -ForegroundColor Red
} else {
    Write-Host "`nLedgerFlow infrastructure is UP." -ForegroundColor Green
    Write-Host "To start the Spring Boot engine run: mvn spring-boot:run" -ForegroundColor Cyan
}
