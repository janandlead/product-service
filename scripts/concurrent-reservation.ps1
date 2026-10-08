# Requires PowerShell 7. Use a fresh ACTIVE product with total=1, reserved=0.
param(
    [Parameter(Mandatory=$true)][long]$ProductId,
    [Parameter(Mandatory=$true)][string]$ServiceToken,
    [string]$BaseUrl = "http://localhost:8083"
)
$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) { throw "PowerShell 7 is required for parallel HTTP requests." }
$firstOrder = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$results = 0,1 | ForEach-Object -Parallel {
    $order = $using:firstOrder + $_
    $body = @{ orderId = $order; items = @(@{productId = $using:ProductId; quantity = 1}) } | ConvertTo-Json -Depth 4
    $response = Invoke-WebRequest -Uri "$using:BaseUrl/internal/api/inventory/reserve" -Method Post -ContentType "application/json" -Body $body -Headers @{
        Authorization = "Bearer $using:ServiceToken"
        "X-Correlation-Id" = "concurrent-$order"
    } -SkipHttpErrorCheck
    [pscustomobject]@{ OrderId = $order; Status = [int]$response.StatusCode; Body = $response.Content }
} -ThrottleLimit 2
$results | Format-Table -Wrap
if (@($results | Where-Object Status -eq 200).Count -ne 1 -or @($results | Where-Object Status -eq 409).Count -ne 1) {
    throw "Expected one 200 and one 409; verify initial stock and service credentials."
}
Invoke-RestMethod -Uri "$BaseUrl/internal/api/inventory/$ProductId" -Headers @{ Authorization = "Bearer $ServiceToken" }

