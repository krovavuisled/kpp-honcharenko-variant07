function Get-ManagedProcess([string]$kind) {
    $pidPath = Join-Path $RuntimeRoot ($kind+'.pid')
    if (-not (Test-Path -LiteralPath $pidPath)) { return $null }
    $managedId = 0
    if (-not [int]::TryParse((Get-Content -LiteralPath $pidPath -Raw).Trim(), [ref]$managedId)) {
        throw "Invalid PID file: $pidPath. Check and remove this stale file before starting."
    }
    $process = Get-Process -Id $managedId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    $paths = @("$env:JAVA_HOME\bin\java.exe")
    $marker = Join-Path $RuntimeRoot 'tomcat'
    if ($kind -eq 'client') {
        $paths += "$env:JAVA_HOME\bin\javaw.exe"
        $marker = Join-Path $ProjectRoot 'client\target\app\waste-collection-client.jar'
    } elseif ($kind -eq 'mysql') {
        $paths = @("$MySqlHome\bin\mysqld.exe")
        $marker = Join-Path $RuntimeRoot 'my.ini'
    }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId=$managedId" -ErrorAction Stop
    if ($process.Path -notin $paths -or -not $details.CommandLine -or
        $details.CommandLine.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "PID file $pidPath points to another process. It will not be stopped or used."
    }
    return $process
}
function Assert-OwnedPort([int]$port, $process) {
    $process.Refresh()
    if ($process.HasExited) { throw "Managed process for port $port has exited. Check .runtime logs." }
    $owners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop |
        Where-Object { $_.LocalAddress -in @('127.0.0.1','0.0.0.0','::') } |
        Select-Object -ExpandProperty OwningProcess -Unique)
    if ($owners.Count -ne 1 -or $owners[0] -ne $process.Id) {
        throw "Port $port is not owned by this project's managed process. No database setup will be attempted."
    }
}
