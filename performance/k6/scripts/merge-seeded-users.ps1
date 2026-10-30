# Merges reports/<tenant>/seededUsers.json (produced by list-seeded-users.ps1)
# into data/users/<tenant>.users.json - appends only, de-duplicated by
# username, so an existing real credential already in the pool file is never
# overwritten or dropped by a bad merge.
#
# Usage:
#   .\scripts\merge-seeded-users.ps1 -Tenant qafeature
#   .\scripts\merge-seeded-users.ps1 -Tenant qahbros

param(
    [Parameter(Mandatory = $true)][string]$Tenant
)

$seededPath = "reports/$Tenant/seededUsers.json"
$poolPath = "data/users/$Tenant.users.json"

if (-not (Test-Path $seededPath)) {
    Write-Error "Not found: $seededPath (run list-seeded-users.ps1 -Tenant $Tenant first)"
    exit 1
}
if (-not (Test-Path $poolPath)) {
    Write-Error "Not found: $poolPath"
    exit 1
}

# ConvertFrom-Json is unreliable in this PowerShell 5.1 environment - for a
# JSON array input it sometimes returns a single not-enumerated wrapper
# object {value: [...], Count: N} instead of the real array, and this isn't
# consistently tied to piped-vs-positional invocation (confirmed by direct
# A/B reproduction - the same call shape gave both correct and wrapped
# results on different runs). The first real merge run on this project hit
# it and corrupted data/users/qafeature.users.json and truncated
# data/users/qahbros.users.json - both had to be manually reconstructed from
# reports/<tenant>/seededUsers.json. Self-heal instead of trusting the shape:
# detect the wrapper and unwrap it before doing anything else with the data.
function ConvertFrom-JsonArraySafe([string]$json) {
    $parsed = @(ConvertFrom-Json $json)
    if ($parsed.Count -eq 1) {
        $names = $parsed[0].PSObject.Properties.Name
        if ($names -contains 'value' -and $names -contains 'Count' -and ($names -notcontains 'username')) {
            return @($parsed[0].value)
        }
    }
    return $parsed
}

$seeded = ConvertFrom-JsonArraySafe (Get-Content $seededPath -Raw)
$existing = ConvertFrom-JsonArraySafe (Get-Content $poolPath -Raw)

$existingNames = @{}
foreach ($e in $existing) { $existingNames[$e.username] = $true }

$added = @()
$merged = @($existing)
foreach ($s in $seeded) {
    if (-not $existingNames.ContainsKey($s.username)) {
        $merged += $s
        $existingNames[$s.username] = $true
        $added += $s.username
    }
}

# Write to a temp file and verify it round-trips to the expected shape and
# count BEFORE touching the real pool file - the incident this script's
# comments describe happened because a failed/malformed write went straight
# to the real file with no check, corrupting one tenant's real credentials
# and truncating another's to empty.
#
# Written via .NET UTF8Encoding($false) rather than Set-Content -Encoding
# utf8, which always emits a BOM in Windows PowerShell 5.1 - k6's own
# JSON.parse(open(...)) (used by lib/userPool.js to load this exact file)
# chokes on a leading BOM with "invalid character" and refuses to run,
# confirmed by direct reproduction after the first merge run silently wrote
# BOM-prefixed pool files.
$tempPath = "$poolPath.tmp"
$jsonText = ConvertTo-Json -InputObject $merged
[System.IO.File]::WriteAllText($tempPath, $jsonText, (New-Object System.Text.UTF8Encoding($false)))
$verify = ConvertFrom-JsonArraySafe (Get-Content $tempPath -Raw)
if ($verify.Count -ne $merged.Count -or ($verify | Where-Object { -not $_.username -or -not $_.password }).Count -gt 0) {
    Remove-Item $tempPath -ErrorAction SilentlyContinue
    Write-Error "Refusing to update $poolPath - the written file did not round-trip to $($merged.Count) valid {username,password} entries (got $($verify.Count)). No changes made."
    exit 1
}
Move-Item -Path $tempPath -Destination $poolPath -Force

Write-Output "Pool for $Tenant`: $($existing.Count) -> $($merged.Count) credential(s) ($($added.Count) newly added)"
if ($added.Count -gt 0) {
    Write-Output "Added: $($added -join ', ')"
}
