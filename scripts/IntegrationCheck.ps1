$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Environment.ps1"
if (-not $Settings) { throw 'Start the local project before running the integration check.' }
$api = $env:API_BASE_URL
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 10)
$assertions = 0
$site = $null
$vehicle = $null
$request = $null
$trip = $null

function Send-Api([string]$method, [string]$path, $body) {
    $parameters = @{ Uri="$api/$path"; Method=$method; TimeoutSec=8; ErrorAction='Stop' }
    if ($null -ne $body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 5 -Compress))
    }
    Invoke-RestMethod @parameters
}

function Check([bool]$condition, [string]$label) {
    if (-not $condition) { throw "FAIL: $label" }
    $script:assertions++
    Write-Host "PASS: $label"
}

function Expect-HttpError([string]$method, [string]$path, $body, [int[]]$expected, [string]$label) {
    $status = 0
    try { $null = Send-Api $method $path $body }
    catch {
        if (-not $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
    }
    Check ($status -in $expected) "$label (HTTP $status)"
}

function Move-Request([long]$id, [string]$status) {
    Send-Api Patch "requests/$id/status" @{status=$status}
}

try {
    foreach ($resource in 'sites','vehicles','requests','trips') {
        $reply = Send-Api Get $resource $null
        Check ($null -ne $reply) "Seed data for $resource"
    }
    $siteBody = @{code="QA-$suffix"; name='Audit fixture'; address='Integration address'; capacityContainers=4}
    $site = Send-Api Post 'sites' $siteBody
    Check ($site.id -gt 0) 'Create a container site'
    $siteBody.capacityContainers = 6
    $site = Send-Api Put "sites/$($site.id)" $siteBody
    Check ((Send-Api Get "sites/$($site.id)" $null).capacityContainers -eq 6) 'Update and read a container site'
    Expect-HttpError Post 'sites' $siteBody @(409,400) 'Reject duplicate site code'
    Expect-HttpError Post 'sites' @{code="BAD-$suffix"; name='Audit fixture'; address='Integration address'; capacityContainers=0} @(400) 'Reject zero site capacity'
    $vehicleBody = @{plate="QA-$suffix"; model='Audit vehicle'; capacityKg=8500; active=$true}
    $vehicle = Send-Api Post 'vehicles' $vehicleBody
    Check ($vehicle.id -gt 0) 'Create a vehicle'
    $vehicleBody.capacityKg = 9000
    $vehicle = Send-Api Put "vehicles/$($vehicle.id)" $vehicleBody
    Check ((Send-Api Get "vehicles/$($vehicle.id)" $null).capacityKg -eq 9000) 'Update and read vehicle payload'
    $requestBody = @{siteId=$site.id; title='Audit pickup'; fillLevel=80; sanitaryRisk=60; overdueHours=36}
    $request = Send-Api Post 'requests' $requestBody
    Check ($request.status -eq 'CREATED') 'Request starts in CREATED'
    Check ([decimal]$request.priority -eq 65.50) 'Priority 80 / 60 / 36 hours equals 65.50'
    Expect-HttpError Patch "requests/$($request.id)/status" @{status='IN_PROGRESS'} @(400,409) 'Reject skipped status'
    Expect-HttpError Patch "requests/$($request.id)/status" @{status='PLANNED'} @(400,409) 'Reject planning without trip'
    Expect-HttpError Delete "sites/$($site.id)" $null @(409,400) 'Keep site referenced by request'
    $requestBody.overdueHours = 100
    $request = Send-Api Put "requests/$($request.id)" $requestBody
    Check ([decimal]$request.priority -eq 78.00) 'Cap overdue score at 100 and recalculate'
    $tripBody = @{requestId=$request.id; vehicleId=$vehicle.id; scheduledDate=(Get-Date).ToString('yyyy-MM-dd'); notes='Audit trip'}
    $trip = Send-Api Post 'trips' $tripBody
    Check ($trip.id -gt 0) 'Create trip with a request and vehicle'
    $tripBody.notes = 'Updated audit trip'
    $trip = Send-Api Put "trips/$($trip.id)" $tripBody
    Check ((Send-Api Get "trips/$($trip.id)" $null).notes -eq 'Updated audit trip') 'Update and read trip'
    Expect-HttpError Post 'trips' $tripBody @(400,409) 'Reject a second trip for the same request'
    Expect-HttpError Delete "vehicles/$($vehicle.id)" $null @(400,409) 'Keep vehicle referenced by trip'
    foreach ($status in 'PLANNED','IN_PROGRESS','COMPLETED') {
        $request = Move-Request $request.id $status
        Check ($request.status -eq $status) "Transition to $status"
    }
    Expect-HttpError Patch "requests/$($request.id)/status" @{status='CREATED'} @(400,409) 'Reject reverse status'
    $persisted = Send-Api Get "requests/$($request.id)" $null
    Check ($persisted.status -eq 'COMPLETED' -and [decimal]$persisted.priority -eq 78.00) 'Read final state and score from MySQL'
    $null = Send-Api Delete "trips/$($trip.id)" $null
    Expect-HttpError Get "trips/$($trip.id)" $null @(404) 'Delete trip'
    $trip = $null
    $null = Send-Api Delete "requests/$($request.id)" $null
    Expect-HttpError Get "requests/$($request.id)" $null @(404) 'Delete request'
    $request = $null
    $null = Send-Api Delete "vehicles/$($vehicle.id)" $null
    Expect-HttpError Get "vehicles/$($vehicle.id)" $null @(404) 'Delete vehicle'
    $vehicle = $null
    $null = Send-Api Delete "sites/$($site.id)" $null
    Expect-HttpError Get "sites/$($site.id)" $null @(404) 'Delete site'
    $site = $null
    Write-Host "Integration checks passed: $assertions"
} finally {
    if ($trip -and $request) {
        try {
            $latest = Send-Api Get "requests/$($request.id)" $null
            if ($latest.status -eq 'PLANNED') { $latest = Move-Request $request.id 'IN_PROGRESS' }
            if ($latest.status -eq 'IN_PROGRESS') { $null = Move-Request $request.id 'COMPLETED' }
        } catch { Write-Warning 'Could not advance the temporary request for cleanup.' }
    }
    foreach ($item in @(@{kind='trips'; value=$trip}, @{kind='requests'; value=$request}, @{kind='vehicles'; value=$vehicle}, @{kind='sites'; value=$site})) {
        if ($item.value) {
            try { $null = Send-Api Delete "$($item.kind)/$($item.value.id)" $null }
            catch { Write-Warning "Could not remove the temporary record $($item.kind)/$($item.value.id)." }
        }
    }
}
