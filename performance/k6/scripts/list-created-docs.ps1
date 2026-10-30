# Extracts the list of real schemeDocNo values created by a k6 run, parsed
# from the console log flows/serviceInvoiceAdhoc.js already writes for every
# Save call (k6 has no native "return an array of strings from a run" -
# every VU is an isolated runtime, so this is the practical way to recover
# the full list afterward instead of a bare count).
#
# Usage:
#   .\scripts\list-created-docs.ps1 -Tenant stage-ideal
#   .\scripts\list-created-docs.ps1 -ConsoleLogPath reports/qafeature/console.log
#
# Run k6 with --console-output=reports/<tenant>/console.log first (all
# scenarios in this project already support that flag).

param(
    [string]$Tenant,
    [string]$ConsoleLogPath
)

if (-not $ConsoleLogPath) {
    if (-not $Tenant) {
        Write-Error "Provide either -Tenant <name> or -ConsoleLogPath <path>"
        exit 1
    }
    $ConsoleLogPath = "reports/$Tenant/console.log"
}

if (-not (Test-Path $ConsoleLogPath)) {
    Write-Error "Console log not found: $ConsoleLogPath (re-run k6 with --console-output=$ConsoleLogPath)"
    exit 1
}

$docNumbers = Select-String -Path $ConsoleLogPath -Pattern 'schemeDocNo=(\S+)' | ForEach-Object {
    $_.Matches[0].Groups[1].Value
} | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne 'undefined' }

Write-Output "Created $($docNumbers.Count) document(s) (from $ConsoleLogPath):"
$i = 1
foreach ($doc in $docNumbers) {
    Write-Output "  $i. $doc"
    $i++
}
