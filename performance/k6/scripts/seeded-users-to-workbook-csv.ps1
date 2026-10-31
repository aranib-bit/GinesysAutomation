# Turns reports/<tenant>/seededUsers.json (produced by list-seeded-users.ps1) into rows for the environment's UsersList.xlsx.
# Performance users live in UsersList.xlsx (usertype [PERF]); k6 gets them from the file the Java side exports. This script only writes
# reports/<tenant>/seededUsers.workbook.csv (username,password,usertype,logintype) - paste its rows under the existing ones, skipping names already there.
#
# Usage:
#   .\scripts\seeded-users-to-workbook-csv.ps1 -Tenant qafeature

param(
    [Parameter(Mandatory = $true)][string]$Tenant
)

$seededPath = "reports/$Tenant/seededUsers.json"
$csvPath = "reports/$Tenant/seededUsers.workbook.csv"

if (-not (Test-Path $seededPath)) {
    Write-Error "Not found: $seededPath (run list-seeded-users.ps1 -Tenant $Tenant first)"
    exit 1
}

# ConvertFrom-Json in Windows PowerShell 5.1 can return a {value, Count} wrapper for a JSON array - unwrap it.
$parsed = @(ConvertFrom-Json (Get-Content $seededPath -Raw))
if ($parsed.Count -eq 1) {
    $names = $parsed[0].PSObject.Properties.Name
    if ($names -contains 'value' -and $names -contains 'Count' -and ($names -notcontains 'username')) { $parsed = @($parsed[0].value) }
}

$rows = @('username,password,usertype,logintype')
foreach ($s in $parsed) {
    if (-not $s.username -or -not $s.password) { continue }
    $rows += ('{0},{1},"[PERF]",ERP' -f $s.username, $s.password)
}
# UTF8Encoding($false): no byte-order mark
[System.IO.File]::WriteAllText($csvPath, ($rows -join "`r`n") + "`r`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Wrote $($rows.Count - 1) row(s) to $csvPath - add them to env/<env>/data/UsersList.xlsx (sheet Users)."