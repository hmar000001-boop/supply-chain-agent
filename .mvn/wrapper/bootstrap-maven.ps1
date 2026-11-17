param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArgs
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$propertiesPath = Join-Path $PSScriptRoot 'maven-wrapper.properties'
if (-not (Test-Path -LiteralPath $propertiesPath)) {
    throw "Missing Maven wrapper properties: $propertiesPath"
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
    if ($line -match '^\s*([^#=]+)=(.*)$') {
        $properties[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$version = $properties['distributionVersion']
$primaryUrl = $properties['distributionUrl']
$fallbackUrl = $properties['distributionFallbackUrl']
$expectedHash = $properties['distributionSha512Sum']
if ([string]::IsNullOrWhiteSpace($version) -or
    [string]::IsNullOrWhiteSpace($primaryUrl) -or
    [string]::IsNullOrWhiteSpace($expectedHash)) {
    throw "Incomplete Maven distribution configuration in $propertiesPath"
}

$mavenUserHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $HOME '.m2' }
$mavenHome = Join-Path $mavenUserHome "wrapper\dists\apache-maven-$version"
$mavenCommand = Join-Path $mavenHome 'bin\mvn.cmd'

if (-not (Test-Path -LiteralPath $mavenCommand)) {
    $cacheParent = Split-Path -Parent $mavenHome
    New-Item -ItemType Directory -Force -Path $cacheParent | Out-Null
    $workDir = Join-Path $env:TEMP ("maven-bootstrap-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $workDir | Out-Null
    try {
        $archive = Join-Path $workDir "apache-maven-$version-bin.zip"
        $urls = @($primaryUrl)
        if (-not [string]::IsNullOrWhiteSpace($fallbackUrl)) { $urls += $fallbackUrl }
        $downloaded = $false

        foreach ($url in $urls) {
            Write-Host "Downloading Apache Maven $version from $url"
            for ($attempt = 1; $attempt -le 6; $attempt++) {
                try {
                    Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing -TimeoutSec 600
                    $downloaded = $true
                    break
                }
                catch {
                    if ($attempt -eq 6) {
                        Write-Warning "Download attempt $attempt failed: $($_.Exception.Message)"
                    }
                    else {
                        Start-Sleep -Seconds ([Math]::Min(20, 3 * $attempt))
                    }
                }
            }
            if ($downloaded) { break }
        }
        if (-not $downloaded) { throw "Unable to download Apache Maven $version from configured Apache URLs" }

        $actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash.ToLowerInvariant()) {
            throw "Apache Maven archive SHA-512 mismatch. Expected $expectedHash; got $actualHash"
        }

        Expand-Archive -LiteralPath $archive -DestinationPath $workDir -Force
        $extractedHome = Join-Path $workDir "apache-maven-$version"
        if (-not (Test-Path -LiteralPath (Join-Path $extractedHome 'bin\mvn.cmd'))) {
            throw 'Downloaded archive did not contain the expected Maven executable'
        }
        if (-not (Test-Path -LiteralPath $mavenCommand)) {
            if (Test-Path -LiteralPath $mavenHome) { Remove-Item -Recurse -Force -LiteralPath $mavenHome }
            Move-Item -LiteralPath $extractedHome -Destination $mavenHome
        }
    }
    finally {
        if (Test-Path -LiteralPath $workDir) { Remove-Item -Recurse -Force -LiteralPath $workDir }
    }
}

& $mavenCommand @MavenArgs
exit $LASTEXITCODE
