$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$ProjectRoot = Split-Path $PSScriptRoot -Parent
if ($ProjectRoot -match '[^\x00-\x7F]') {
    throw 'Use an ASCII-only full project path (Latin folder names) before Setup/Start. MySQL on Windows does not reliably support Unicode directory names. No database setup was attempted.'
}
$tools = Join-Path $ProjectRoot '.tools'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$cache = Join-Path $tools 'downloads'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
function Get-ReadyTool([string]$Pattern,[string[]]$RequiredFiles) {
    foreach ($directory in (Get-ChildItem -LiteralPath $tools -Directory -Filter $Pattern)) {
        $complete = $true
        foreach ($relative in $RequiredFiles) {
            if (-not (Test-Path -LiteralPath (Join-Path $directory.FullName $relative) -PathType Leaf)) { $complete = $false; break }
        }
        if ($complete) { return $directory.FullName }
    }
}
function Download-Zip([string]$Name,[string]$Pattern,[string[]]$Urls,[string[]]$HashUrls,[string]$Algorithm,[string[]]$RequiredFiles) {
    if (Get-ReadyTool $Pattern $RequiredFiles) { return }
    $zip = Join-Path $cache ($Name+'.zip')
    $downloaded = $false
    for ($index=0; $index -lt $Urls.Count; $index++) {
        try {
            Write-Host "Downloading $Name..."
            Invoke-WebRequest -UseBasicParsing -Uri $Urls[$index] -OutFile $zip
            if ($HashUrls -and $HashUrls.Count -gt $index -and $HashUrls[$index]) {
                $expected = ((Invoke-WebRequest -UseBasicParsing -Uri $HashUrls[$index]).Content.Trim() -split '\s+')[0]
                if ((Get-FileHash -LiteralPath $zip -Algorithm $Algorithm).Hash -ne $expected) { throw "$Name checksum mismatch" }
            }
            $downloaded = $true
            break
        } catch {
            if ($index -eq $Urls.Count-1) { throw }
            Write-Warning "$Name download failed; trying the official Apache archive."
        }
    }
    if (-not $downloaded) { throw "No download source is available for $Name." }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
    if (-not (Get-ReadyTool $Pattern $RequiredFiles)) { throw "$Name extraction is incomplete. Run Setup.ps1 again to repair it." }
}
function Check-Version([string]$Executable,[string[]]$Arguments,[string]$Label,[string]$Help) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.WorkingDirectory = $ProjectRoot
    if ([IO.Path]::GetExtension($Executable) -eq '.cmd') {
        $info.FileName = $env:ComSpec
        $info.Arguments = '/d /s /c ""' + $Executable + '" ' + ($Arguments -join ' ') + '"'
    } else {
        $info.FileName = $Executable
        $info.Arguments = $Arguments -join ' '
    }
    $process = $null
    try {
        $process = [Diagnostics.Process]::Start($info)
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(30000)) { $process.Kill(); throw "$Label version check timed out. $Help" }
        $out = $stdout.GetAwaiter().GetResult()
        $err = $stderr.GetAwaiter().GetResult()
        if ($out) { Write-Host $out.TrimEnd() }
        if ($err) { Write-Host $err.TrimEnd() }
        if ($process.ExitCode -ne 0) { throw "$Label could not run (exit $($process.ExitCode)). $Help" }
    } catch {
        throw "$Label version check failed. $Help Details: $($_.Exception.Message)"
    } finally {
        if ($process) { $process.Dispose() }
    }
}
$jdkFiles = @('bin\java.exe','bin\javaw.exe','bin\javac.exe')
if (-not (Get-ReadyTool 'jdk-21*' $jdkFiles)) {
    $jdk = (Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0].binary.package
    if (-not $jdk.link -or -not $jdk.checksum) { throw 'Adoptium did not return a Windows x64 JDK 21 package.' }
    $zip = Join-Path $cache 'jdk.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $jdk.link -OutFile $zip
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $jdk.checksum) { throw 'JDK checksum mismatch' }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
    if (-not (Get-ReadyTool 'jdk-21*' $jdkFiles)) { throw 'JDK extraction is incomplete. Run Setup.ps1 again to repair it.' }
}
Download-Zip -Name 'Maven' -Pattern 'apache-maven-*' -Urls @('https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip','https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip') -HashUrls @('https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512','https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512') -Algorithm 'SHA512' -RequiredFiles @('bin\mvn.cmd')
Download-Zip -Name 'Tomcat' -Pattern 'apache-tomcat-9*' -Urls @('https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.124/bin/apache-tomcat-9.0.124-windows-x64.zip','https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.124/bin/apache-tomcat-9.0.124-windows-x64.zip') -HashUrls @('https://downloads.apache.org/tomcat/tomcat-9/v9.0.124/bin/apache-tomcat-9.0.124-windows-x64.zip.sha512','https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.124/bin/apache-tomcat-9.0.124-windows-x64.zip.sha512') -Algorithm 'SHA512' -RequiredFiles @('bin\bootstrap.jar','bin\tomcat-juli.jar','conf\server.xml')
Download-Zip -Name 'MySQL' -Pattern 'mysql-*-winx64' -Urls @('https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.13-winx64.zip') -RequiredFiles @('bin\mysqld.exe','bin\mysql.exe','bin\mysqladmin.exe')
. "$PSScriptRoot\Environment.ps1"
Check-Version "$env:JAVA_HOME\bin\java.exe" @('-version') 'JDK 21' 'Run Setup.ps1 again if its files are incomplete.'
Check-Version "$MavenHome\bin\mvn.cmd" @('-version') 'Maven' 'Check the downloaded JDK and Maven directories.'
Check-Version "$MySqlHome\bin\mysqld.exe" @('--version') 'MySQL' 'MySQL requires the Microsoft Visual C++ x64 Redistributable. Official download: https://aka.ms/vc14/vc_redist.x64.exe . Install it if Windows reports missing runtime DLLs, then run Setup.ps1 again.'
if (-not (Test-Path -LiteralPath $SettingsFile)) {
    $Settings = [pscustomobject]@{ mysqlPort=3309; httpPort=8087; databasePassword=([guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')) }
    $Settings | ConvertTo-Json | Set-Content -LiteralPath $SettingsFile -Encoding UTF8
    Write-Host 'Local settings created. If needed, change the ports in .runtime/settings.json before the first start.'
}
Write-Host 'Portable tools are ready. Run Start.cmd.'
