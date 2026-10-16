# PowerShell script to stop LedgerFlow local containers
Param(
    [switch]$CleanVolumes = $false
)

Write-Host "Stopping LedgerFlow containers..." -ForegroundColor Yellow

if ($CleanVolumes) {
    Write-Host "Teardown with volume removal requested." -ForegroundColor Magenta
    docker compose --profile full down -v
} else {
    docker compose --profile full down
}

Write-Host "LedgerFlow containers stopped." -ForegroundColor Green
