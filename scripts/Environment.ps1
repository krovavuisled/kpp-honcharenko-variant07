$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$ProjectRoot = Split-Path $PSScriptRoot -Parent
if ($ProjectRoot -match '[^\x00-\x7F]') {
    throw 'Use an ASCII-only full project path (Latin folder names) before Setup/Start. MySQL on Windows does not reliably support Unicode directory names. No database setup was attempted.'
}
$ToolsRoot = Join-Path $ProjectRoot '.tools'
$RuntimeRoot = Join-Path $ProjectRoot '.runtime'
function Find-ToolDirectory([string]$Pattern,[string[]]$RequiredFiles) {
    foreach ($directory in (Get-ChildItem -LiteralPath $ToolsRoot -Directory -Filter $Pattern -ErrorAction SilentlyContinue)) {
        $complete = $true
        foreach ($relative in $RequiredFiles) {
            if (-not (Test-Path -LiteralPath (Join-Path $directory.FullName $relative) -PathType Leaf)) { $complete = $false; break }
        }
        if ($complete) { return $directory.FullName }
    }
    throw "Tool $Pattern is missing or incomplete. Run scripts\Setup.ps1 first."
}
$env:JAVA_HOME = Find-ToolDirectory 'jdk-21*' @('bin\java.exe','bin\javaw.exe','bin\javac.exe')
$MavenHome = Find-ToolDirectory 'apache-maven-*' @('bin\mvn.cmd')
$TomcatHome = Find-ToolDirectory 'apache-tomcat-9*' @('bin\bootstrap.jar','bin\tomcat-juli.jar','conf\server.xml')
$MySqlHome = Find-ToolDirectory 'mysql-*-winx64' @('bin\mysqld.exe','bin\mysql.exe','bin\mysqladmin.exe')
$env:PATH = "$env:JAVA_HOME\bin;$MavenHome\bin;$MySqlHome\bin;$env:PATH"
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
$env:MAVEN_SKIP_RC = '1'
New-Item -ItemType Directory -Force -Path $RuntimeRoot | Out-Null
$SettingsFile = Join-Path $RuntimeRoot 'settings.json'
$Settings = $null
if (Test-Path -LiteralPath $SettingsFile) {
    $Settings = Get-Content -LiteralPath $SettingsFile -Raw | ConvertFrom-Json
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$($Settings.mysqlPort)/waste_collection?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC"
    $env:DB_USER = 'waste_app'
    $env:DB_PASSWORD = $Settings.databasePassword
    $env:API_BASE_URL = "http://127.0.0.1:$($Settings.httpPort)/waste-collection-server/api"
}
