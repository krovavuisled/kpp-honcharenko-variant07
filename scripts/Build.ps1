. "$PSScriptRoot\Environment.ps1"
$mavenSettings = Join-Path $PSScriptRoot 'maven-settings.xml'
foreach ($module in @('server','client')) {
    & "$MavenHome\bin\mvn.cmd" -s $mavenSettings -gs $mavenSettings "-Dmaven.repo.local=$ToolsRoot\m2" -f "$ProjectRoot\$module\pom.xml" clean verify
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $module" }
}
