# Extracts the perftest username/password pairs a seedPerfUsers.js run
# actually created, parsed from the console log flows/createUser.js writes
# for every successful CreateAppUser call (k6 has no native "return an array
# of strings from a run" - every VU is an isolated runtime, so this is the
# practical way to recover the full list afterward, same convention as
# list-created-docs.ps1 uses for schemeDocNo).
#
# Usage:
#   .\scripts\list-seeded-users.ps1 -Tenant qahbros
#   .\scripts\list-seeded-users.ps1 -ConsoleLogPath reports/qahbros/smoketest-console.log
#
# Run k6 with --console-output=reports/<tenant>/console.log first (
# scenarios/backfill/seedPerfUsers.js supports that flag like every other scenario).
#
# Writes reports/<tenant>/seededUsers.json (or alongside -ConsoleLogPath if
# -Tenant wasn't given) - merging that into data/users/<tenant>.users.json is
# a manual, reviewed step, not automatic, so the existing real accounts in
# that file are never at risk of being clobbered by a script bug.

param(
    [string]$Tenant,
    [string]$ConsoleLogPath,
    [string]$OutPath
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

if (-not $OutPath) {
    $OutPath = if ($Tenant) { "reports/$Tenant/seededUsers.json" } else { Join-Path (Split-Path $ConsoleLogPath) "seededUsers.json" }
}

# Wrapped in @(...) so a single match still yields a 1-element array rather
# than PowerShell unwrapping it to a bare scalar (Windows PowerShell 5.1 has
# no ConvertTo-Json -AsArray to compensate for that the way PS7 does).
$users = @(Select-String -Path $ConsoleLogPath -Pattern 'SeededUser: ([^|\s]+)\|([^"\s]+)' | ForEach-Object {
    [PSCustomObject]@{
        username = $_.Matches[0].Groups[1].Value
        password = $_.Matches[0].Groups[2].Value
    }
})

Write-Output "Seeded $($users.Count) user(s) (from $ConsoleLogPath):"
$i = 1
foreach ($u in $users) {
    Write-Output "  $i. $($u.username)"
    $i++
}

# Passed as -InputObject (not piped) so the array survives ConvertTo-Json
# even at 0 or 1 elements, instead of collapsing to a bare object/nothing.
# Written via .NET UTF8Encoding($false) rather than Set-Content -Encoding
# utf8 (which always emits a BOM in Windows PowerShell 5.1) - a BOM here
# would propagate into data/users/<tenant>.users.json via merge-seeded-
# users.ps1, and k6's JSON.parse(open(...)) refuses to parse a leading BOM.
$jsonText = ConvertTo-Json -InputObject $users
[System.IO.File]::WriteAllText($OutPath, $jsonText, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Wrote $OutPath"
