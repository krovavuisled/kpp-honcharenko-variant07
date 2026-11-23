param([switch]$NoClient,[switch]$SkipBuild)
. "$PSScriptRoot\Environment.ps1"
. "$PSScriptRoot\Runtime.ps1"
function Assert-PortAvailable([int]$port, $managedProcess) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    $listener.ExclusiveAddressUse = $true
    $free = $false
    try { $listener.Start(); $free = $true } catch [Net.Sockets.SocketException] { } finally { $listener.Stop() }
    if ($free) {
        if ($managedProcess) { throw "A managed process is running on another port. Run Stop.cmd with the previous settings before changing ports." }
        return
    }
    if ($managedProcess) { Assert-OwnedPort $port $managedProcess; return }
    throw "Port $port is already in use by another program. No database setup was attempted. Choose free mysqlPort/httpPort values in .runtime/settings.json and retry."
}
function Wait-Endpoint($uri, $serverProcess) {
    for ($i=0; $i -lt 60; $i++) {
        $serverProcess.Refresh()
        if ($serverProcess.HasExited) { throw 'Tomcat exited. Check .runtime/tomcat.stderr.log.' }
        try {
            Assert-OwnedPort ([int]$Settings.httpPort) $serverProcess
            return Invoke-RestMethod -Uri $uri -TimeoutSec 2
        } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Server did not become ready: $uri. Check .runtime logs."
}
function Invoke-MySql([string]$sql,[string]$user='root',[switch]$WithoutPassword) {
    $env:MYSQL_PWD = if ($WithoutPassword) { '' } else { $Settings.databasePassword }
    try {
        $sql | & "$MySqlHome\bin\mysql.exe" --no-defaults --host=127.0.0.1 "--port=$($Settings.mysqlPort)" "--user=$user" --default-character-set=utf8mb4
        if ($LASTEXITCODE -ne 0) { throw 'MySQL operation failed.' }
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
if (-not $Settings) {
    $Settings = [pscustomobject]@{ mysqlPort=3309; httpPort=8087; databasePassword=([guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')) }
    $Settings | ConvertTo-Json | Set-Content -LiteralPath $SettingsFile -Encoding utf8
    . "$PSScriptRoot\Environment.ps1"
}
$mysqlPort = [int]$Settings.mysqlPort
$httpPort = [int]$Settings.httpPort
if ($mysqlPort -lt 1 -or $mysqlPort -gt 65535 -or $httpPort -lt 1 -or $httpPort -gt 65535 -or $mysqlPort -eq $httpPort) {
    throw 'mysqlPort and httpPort must be different integers between 1 and 65535 in .runtime/settings.json.'
}
$managedMySql = Get-ManagedProcess 'mysql'
$managedTomcat = Get-ManagedProcess 'tomcat'
$managedClient = Get-ManagedProcess 'client'
Assert-PortAvailable $mysqlPort $managedMySql
Assert-PortAvailable $httpPort $managedTomcat
# Release the client JARs before a rebuild and always redeploy the current WAR.
foreach ($kind in @('client','tomcat')) {
    $pidFile = Join-Path $RuntimeRoot ($kind+'.pid')
    if (Test-Path -LiteralPath $pidFile) {
        $managed = if ($kind -eq 'client') { $managedClient } else { $managedTomcat }
        if ($managed) {
            Stop-Process -Id $managed.Id
            Wait-Process -Id $managed.Id -Timeout 15 -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $pidFile
    }
}
$data = Join-Path $RuntimeRoot 'mysql-data'
$fresh = -not (Test-Path -LiteralPath (Join-Path $data 'mysql'))
$mysqlConfig = Join-Path $RuntimeRoot 'my.ini'
$baseSlash = $MySqlHome.Replace('\','/')
$dataSlash = $data.Replace('\','/')
@"
[mysqld]
basedir=$baseSlash
datadir=$dataSlash
port=$($Settings.mysqlPort)
bind-address=127.0.0.1
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
"@ | ForEach-Object { [IO.File]::WriteAllText($mysqlConfig,$_,[Text.UTF8Encoding]::new($false)) }
if ($fresh) {
    & "$MySqlHome\bin\mysqld.exe" "--defaults-file=$mysqlConfig" --initialize-insecure
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
}
$mysqlPid = Join-Path $RuntimeRoot 'mysql.pid'
$running = $null -ne $managedMySql
if (-not $running) {
    Assert-PortAvailable $mysqlPort $null
    $mysqlProcess = Start-Process -FilePath "$MySqlHome\bin\mysqld.exe" -ArgumentList "`"--defaults-file=$mysqlConfig`" --no-monitor --console" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimeRoot\mysql.stdout.log" -RedirectStandardError "$RuntimeRoot\mysql.stderr.log"
    $mysqlProcess.Id | Set-Content -LiteralPath $mysqlPid
    $managedMySql = $mysqlProcess
}
$mysqlReady = $false
for ($i=0;$i -lt 60;$i++) {
    $managedMySql.Refresh()
    if ($managedMySql.HasExited) { throw 'MySQL exited. Check .runtime/mysql.stderr.log and the Visual C++ prerequisite in README.' }
    try { Assert-OwnedPort $mysqlPort $managedMySql; $mysqlReady = $true; break } catch { Start-Sleep -Milliseconds 500 }
}
if (-not $mysqlReady) { throw 'Managed MySQL did not become ready. No database setup was attempted.' }
$bootstrapMarker = Join-Path $RuntimeRoot 'database-ready.txt'
if (-not (Test-Path -LiteralPath $bootstrapMarker)) {
    $secret = $Settings.databasePassword
    $env:MYSQL_PWD = $secret
    $probeErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 promotes native stderr to an error even when
        # redirected. A rejected password is expected before the first ALTER USER.
        $ErrorActionPreference = 'Continue'
        & "$MySqlHome\bin\mysql.exe" --no-defaults --host=127.0.0.1 "--port=$($Settings.mysqlPort)" --user=root --execute='SELECT 1' *> $null
        $rootConfigured = $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $probeErrorActionPreference
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    if (-not $rootConfigured) { Invoke-MySql "ALTER USER 'root'@'localhost' IDENTIFIED BY '$secret';" -WithoutPassword }
    Invoke-MySql (Get-Content -LiteralPath "$ProjectRoot\database.sql" -Raw -Encoding UTF8)
    Invoke-MySql "CREATE USER IF NOT EXISTS 'waste_app'@'127.0.0.1' IDENTIFIED BY '$secret'; GRANT SELECT,INSERT,UPDATE,DELETE ON waste_collection.* TO 'waste_app'@'127.0.0.1';"
    'Database schema and local account initialized.' | Set-Content -LiteralPath $bootstrapMarker
}
if (-not $SkipBuild) { & "$PSScriptRoot\Build.ps1" }
$catalinaBase = Join-Path $RuntimeRoot 'tomcat'
foreach ($dir in @('conf','logs','temp','work','webapps')) { New-Item -ItemType Directory -Force -Path (Join-Path $catalinaBase $dir) | Out-Null }
if (-not (Test-Path -LiteralPath "$catalinaBase\conf\server.xml")) {
    Copy-Item -Path "$TomcatHome\conf\*" -Destination "$catalinaBase\conf" -Recurse
    $xml = Get-Content -LiteralPath "$catalinaBase\conf\server.xml" -Raw
    $xml = $xml.Replace('port="8005"','port="-1"').Replace('port="8080"',"address=`"127.0.0.1`" port=`"$($Settings.httpPort)`"")
    $xml | Set-Content -LiteralPath "$catalinaBase\conf\server.xml" -Encoding utf8
}
Assert-PortAvailable $httpPort $null
Copy-Item -LiteralPath "$ProjectRoot\server\target\waste-collection-server.war" -Destination "$catalinaBase\webapps\waste-collection-server.war" -Force
$argsJava = @('-Dfile.encoding=UTF-8',"`"-Dcatalina.home=$TomcatHome`"","`"-Dcatalina.base=$catalinaBase`"","`"-Djava.io.tmpdir=$catalinaBase\temp`"",'-cp',"`"$TomcatHome\bin\bootstrap.jar;$TomcatHome\bin\tomcat-juli.jar`"",'org.apache.catalina.startup.Bootstrap','start')
$serverProcess = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $argsJava -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimeRoot\tomcat.stdout.log" -RedirectStandardError "$RuntimeRoot\tomcat.stderr.log"
$serverProcess.Id | Set-Content -LiteralPath "$RuntimeRoot\tomcat.pid"
$null = Wait-Endpoint "$env:API_BASE_URL/sites" $serverProcess
Write-Host "Server ready: $env:API_BASE_URL"
if (-not $NoClient) { & "$PSScriptRoot\Client.ps1" }
