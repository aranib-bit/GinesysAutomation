param(
    [string]$ConfigPath = "erp/config/multiTenantRun.json",
    # Overrides every tenant's DURATION at runtime instead of editing the JSON
    # file - accepts any k6 duration string (e.g. 20m, 50m, 1h30m). Leave unset
    # to fall back to whatever each tenant's "env.DURATION" already says.
    [string]$Duration
)

$ErrorActionPreference = "Stop"

$config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$scenario = $config.scenario

Write-Output "Running '$scenario' concurrently against $($config.tenants.Count) tenant(s): $(($config.tenants | ForEach-Object { $_.tenant }) -join ', ')"
if ($Duration) {
    Write-Output "Overriding DURATION=$Duration for all tenants (runtime override)"
}

# k6's handleSummary() writer doesn't create missing directories - make sure
# each tenant's report folder exists before launching, or the run completes
# but the summary.json/summary.html write fails at the end.
foreach ($t in $config.tenants) {
    New-Item -ItemType Directory -Force -Path "reports/$($t.tenant)" | Out-Null
}

$jobs = foreach ($t in $config.tenants) {
    $tenantName = $t.tenant
    $envArgs = @("-e", "TENANT=$tenantName")
    if ($t.env) {
        foreach ($prop in $t.env.PSObject.Properties) {
            if ($Duration -and $prop.Name -eq "DURATION") { continue }
            $envArgs += @("-e", "$($prop.Name)=$($prop.Value)")
        }
    }
    if ($Duration) {
        $envArgs += @("-e", "DURATION=$Duration")
    }
    $consoleLogPath = "reports/$tenantName/console.log"
    # Optional per-tenant stagger (e.g. 60 = start this tenant 1 minute after the others).
    $startDelay = 0
    if ($t.startDelaySeconds) { $startDelay = [int]$t.startDelaySeconds }

    Start-Job -Name $tenantName -ScriptBlock {
        param($scenario, $envArgs, $consoleLogPath, $workDir, $startDelay)
        if ($startDelay -gt 0) { Start-Sleep -Seconds $startDelay }
        Set-Location $workDir
        & k6 run $scenario @envArgs --console-output=$consoleLogPath 2>&1
        "EXIT_CODE:$LASTEXITCODE"
    } -ArgumentList $scenario, $envArgs, $consoleLogPath, (Get-Location).Path, $startDelay
}

Write-Output "Launched $($jobs.Count) concurrent k6 process(es), waiting..."
Wait-Job $jobs | Out-Null

foreach ($job in $jobs) {
    Write-Output ""
    Write-Output "=== $($job.Name) output ==="
    Receive-Job $job
    Remove-Job $job
}

Write-Output ""
Write-Output "=== Combined summary ==="
foreach ($t in $config.tenants) {
    $tenantName = $t.tenant
    $summaryPath = "reports/$tenantName/summary.json"
    $consoleLogPath = "reports/$tenantName/console.log"

    if (-not (Test-Path $summaryPath)) {
        Write-Output "$tenantName : no summary.json found (run likely aborted early - check output above)"
        continue
    }

    $summary = Get-Content $summaryPath -Raw | ConvertFrom-Json
    $m = $summary.metrics

    $checksRate = "n/a"
    if ($m.checks) { $checksRate = "{0:N2}%" -f ($m.checks.values.rate * 100) }
    $failedRate = "n/a"
    if ($m.http_req_failed) { $failedRate = "{0:N2}%" -f ($m.http_req_failed.values.rate * 100) }
    $reqCount = "n/a"
    if ($m.http_reqs) { $reqCount = $m.http_reqs.values.count }

    $docCount = 0
    if (Test-Path $consoleLogPath) {
        $docCount = (Select-String -Path $consoleLogPath -Pattern 'Save \[.*-> schemeDocNo=(?!null)\S+' | Measure-Object).Count
        # Billing-by-Brand import scenario: count completed per-user import jobs instead.
        $importsDone = (Select-String -Path $consoleLogPath -Pattern 'billingImport.{0,400}?status[^,]{0,6}Completed' | Measure-Object).Count
        if ($importsDone -gt 0) { $docCount = "$importsDone import job(s) completed" }
    }

    Write-Output "$tenantName : http_reqs=$reqCount checks_pass=$checksRate http_req_failed=$failedRate docs_created=$docCount"
}
