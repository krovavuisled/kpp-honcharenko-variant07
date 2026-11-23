param([string]$ScreenshotDirectory='', [string]$ScreenshotImportFile='')
. "$PSScriptRoot\Environment.ps1"
. "$PSScriptRoot\Runtime.ps1"
if (-not $Settings) { throw 'Run Start.cmd to prepare the local server first.' }
$pidFile = Join-Path $RuntimeRoot 'client.pid'
if (Get-ManagedProcess 'client') {
    Write-Host 'JavaFX client is already running.'
    return
}
$launch = Join-Path $ProjectRoot 'client\target\app'
if (-not (Test-Path -LiteralPath "$launch\waste-collection-client.jar")) { throw 'Client distribution missing. Run scripts\Build.ps1.' }
$jfx = Join-Path $ProjectRoot 'client\target\javafx'
$argsJava = @('-Dfile.encoding=UTF-8',"`"-Dapi.baseUrl=$env:API_BASE_URL`"")
if ($ScreenshotDirectory) {
    $shotPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ScreenshotDirectory)
    $argsJava += "`"-Dapp.screenshotDir=$shotPath`""
    if ($ScreenshotImportFile) {
        $importPath = (Resolve-Path -LiteralPath $ScreenshotImportFile).Path
        $argsJava += "`"-Dapp.screenshotImportFile=$importPath`""
    }
}
$argsJava += @('--module-path',"`"$jfx`"",'--add-modules','javafx.controls,javafx.swing','-jar',"`"$launch\waste-collection-client.jar`"")
$clientProcess = Start-Process -FilePath "$env:JAVA_HOME\bin\javaw.exe" -ArgumentList $argsJava -WorkingDirectory $ProjectRoot -WindowStyle Normal -PassThru -RedirectStandardOutput "$RuntimeRoot\client.stdout.log" -RedirectStandardError "$RuntimeRoot\client.stderr.log"
$clientProcess.Id | Set-Content -LiteralPath "$RuntimeRoot\client.pid"
Write-Host 'JavaFX client started.'
