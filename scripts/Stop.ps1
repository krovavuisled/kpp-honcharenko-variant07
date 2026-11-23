. "$PSScriptRoot\Environment.ps1"
. "$PSScriptRoot\Runtime.ps1"
$managedMySql = Get-ManagedProcess 'mysql'
if ($managedMySql) {
    if (-not $Settings) { throw 'Missing settings for the managed MySQL instance.' }
    Assert-OwnedPort ([int]$Settings.mysqlPort) $managedMySql
}
foreach ($kind in @('client','tomcat')) {
    $pidFile = Join-Path $RuntimeRoot ($kind+'.pid')
    if (Test-Path -LiteralPath $pidFile) {
        $process = Get-ManagedProcess $kind
        if ($process) {
            Stop-Process -Id $process.Id
            Wait-Process -Id $process.Id -Timeout 15 -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $pidFile
    }
}
if ($managedMySql) {
    $env:MYSQL_PWD = $Settings.databasePassword
    try {
        & "$MySqlHome\bin\mysqladmin.exe" --no-defaults --host=127.0.0.1 "--port=$($Settings.mysqlPort)" --user=root shutdown
        if ($LASTEXITCODE -ne 0) { throw 'Could not shut down the managed MySQL instance.' }
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
Remove-Item -LiteralPath (Join-Path $RuntimeRoot 'mysql.pid') -ErrorAction SilentlyContinue
Write-Host 'Coursework processes stopped.'
