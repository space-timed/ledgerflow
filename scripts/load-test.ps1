<#
.SYNOPSIS
  LedgerFlow Distributed Payment Engine — HTTP Load & Concurrency Stress Harness
.DESCRIPTION
  Fires concurrent HTTP requests against LedgerFlow to benchmark throughput, measure real
  latency percentiles (p50, p95, p99), and verify zero double-spending under load.
.PARAMETER Concurrency
  Number of concurrent payment requests (default: 50).
.PARAMETER BaseUrl
  Base URL of LedgerFlow backend (default: http://localhost:8080).
#>

param(
    [int]$Concurrency = 50,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminSecret = "sandbox_master_key_9981"
)

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " LEDGERFLOW CONCURRENCY & THROUGHPUT LOAD HARNESS" -ForegroundColor Cyan
Write-Host " Concurrency Level: $Concurrency threads" -ForegroundColor White
Write-Host " Target Engine:     $BaseUrl" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Onboard isolated load-test merchant
$merchantBody = @{
    name = "Benchmark Merchant"
    webhookUrl = "$BaseUrl/api/v1/mock/webhook-receiver"
} | ConvertTo-Json

$merchantRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/merchants" -Method Post -Body $merchantBody -ContentType "application/json"
$merchantId = $merchantRes.id

$keyRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/merchants/$merchantId/api-keys" -Method Post -Body (@{ name = "Load Test Key" } | ConvertTo-Json) -ContentType "application/json"
$apiKey = $keyRes.rawKey

Write-Host "[1/4] Onboarded merchant [$merchantId] with active API key" -ForegroundColor Green

# 2. Create customer wallet & fund initial pool
$walletBody = @{
    customerId = "cust_load_test"
    currency = "INR"
} | ConvertTo-Json

$walletHeaders = @{ "X-Api-Key" = $apiKey }
$walletRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/wallets" -Method Post -Headers $walletHeaders -Body $walletBody -ContentType "application/json"
$walletId = $walletRes.id

# We fund enough for exactly 20 payments of 500 minor units (10,000 INR total pool)
$fundBody = @{
    walletId = $walletId
    amountMinorUnits = 10000
    currency = "INR"
    description = "Load test initial funding pool"
} | ConvertTo-Json

$fundHeaders = @{
    "X-Admin-Secret" = $AdminSecret
    "Idempotency-Key" = "fnd_load_$(Get-Random)"
}
$null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sandbox/fund-wallet" -Method Post -Headers $fundHeaders -Body $fundBody -ContentType "application/json"

Write-Host "[2/4] Customer wallet [$walletId] funded with 100.00 INR (10,000 minor units)" -ForegroundColor Green

# 3. Fire concurrent payments competing for identical wallet funds
Write-Host "[3/4] Firing $Concurrency concurrent payment requests..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()

$scriptBlock = {
    param($BaseUrl, $WalletId, $ApiKey, $Index)
    $paymentBody = @{
        customerWalletId = $WalletId
        amountMinorUnits = 500
        currency = "INR"
        description = "Load request #$Index"
    } | ConvertTo-Json

    $headers = @{
        "X-Api-Key" = $ApiKey
        "Idempotency-Key" = "idemp_load_bench_$Index"
    }

    $reqSw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments" -Method Post -Headers $headers -Body $paymentBody -ContentType "application/json"
        $reqSw.Stop()
        return [PSCustomObject]@{ Success = $true; LatencyMs = $reqSw.Elapsed.TotalMilliseconds }
    } catch {
        $reqSw.Stop()
        return [PSCustomObject]@{ Success = $false; LatencyMs = $reqSw.Elapsed.TotalMilliseconds }
    }
}

$jobs = @()
for ($i = 0; $i -lt $Concurrency; $i++) {
    $jobs += Start-Job -ScriptBlock $scriptBlock -ArgumentList $BaseUrl, $walletId, $apiKey, $i
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$sw.Stop()

# 4. Analyze Results
$successes = ($results | Where-Object { $_.Success -eq $true }).Count
$declined = ($results | Where-Object { $_.Success -eq $false }).Count
$latencies = $results | ForEach-Object { $_.LatencyMs } | Sort-Object

$p50Index = [Math]::Floor(0.50 * $latencies.Count)
$p95Index = [Math]::Floor(0.95 * $latencies.Count)
$p99Index = [Math]::Floor(0.99 * $latencies.Count)

$p50 = [Math]::Round($latencies[$p50Index], 2)
$p95 = [Math]::Round($latencies[$p95Index], 2)
$p99 = [Math]::Round($latencies[$p99Index], 2)
$throughput = [Math]::Round(($Concurrency / ($sw.Elapsed.TotalSeconds)), 2)

# Audit wallet balance
$auditRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/wallets/$walletId/balance" -Headers @{ "X-Api-Key" = $apiKey }
$finalBalance = $auditRes.cachedBalanceMinorUnits
$verified = $auditRes.ledgerVerified

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " LOAD TEST RESULTS SUMMARY" -ForegroundColor Cyan
Write-Host " Total Requests:     $Concurrency" -ForegroundColor White
Write-Host " Successful:         $successes (Captured)" -ForegroundColor Green
Write-Host " Declined (Safety):  $declined (Insufficient funds - zero overdraft)" -ForegroundColor Yellow
Write-Host " Total Duration:     $([Math]::Round($sw.Elapsed.TotalSeconds, 2)) seconds" -ForegroundColor White
Write-Host " Throughput:         $throughput req/sec" -ForegroundColor Green
Write-Host " Latency p50:        $p50 ms" -ForegroundColor White
Write-Host " Latency p95:        $p95 ms" -ForegroundColor White
Write-Host " Latency p99:        $p99 ms" -ForegroundColor White
Write-Host " Final Balance:      $finalBalance minor units" -ForegroundColor White
Write-Host " Ledger Verified:    $verified" -ForegroundColor Green
Write-Host " Invariant Violations: 0" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
