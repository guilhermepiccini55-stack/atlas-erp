$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$propsFile = Join-Path $baseDir '.mvn\wrapper\maven-wrapper.properties'
$line = Get-Content $propsFile | Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1
if (-not $line) { throw "distributionUrl não encontrado em $propsFile" }
$url = ($line -replace '^distributionUrl=', '').Trim()

$name = [System.IO.Path]::GetFileName($url) -replace '-bin\.zip$', ''
$sha = [System.Security.Cryptography.SHA256]::Create()
$hash = ([System.BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($url)))).Replace('-', '').Substring(0, 16)

if ($env:MAVEN_USER_HOME) { $mavenHome = $env:MAVEN_USER_HOME } else { $mavenHome = Join-Path $env:USERPROFILE '.m2' }
$dist = Join-Path $mavenHome "wrapper\dists\$name\$hash"
$mvn = Join-Path $dist "$name\bin\mvn.cmd"

if (-not (Test-Path $mvn)) {
    Write-Host "Baixando Maven: $url"
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    $zip = Join-Path $dist "$name-bin.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $dist -Force
    Remove-Item $zip -Force
}

& $mvn @args
exit $LASTEXITCODE
