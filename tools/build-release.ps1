param(
    [string]$SigningFile = (Join-Path $env:USERPROFILE '.android\iskrio-release\signing.dpapi.json')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $SigningFile)) {
    throw 'Local signing credentials were not found. See docs/RELEASE.md.'
}
$signing = Get-Content -LiteralPath $SigningFile -Raw | ConvertFrom-Json
$secure = ConvertTo-SecureString $signing.password
$password = [System.Net.NetworkCredential]::new('', $secure).Password
$names = @('ISKRIO_KEYSTORE_FILE', 'ISKRIO_STORE_PASSWORD', 'ISKRIO_KEY_ALIAS', 'ISKRIO_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:ISKRIO_KEYSTORE_FILE = $signing.storeFile
    $env:ISKRIO_STORE_PASSWORD = $password
    $env:ISKRIO_KEY_ALIAS = $signing.keyAlias
    $env:ISKRIO_KEY_PASSWORD = $password
    & (Join-Path $PSScriptRoot '..\gradlew.bat') -p (Join-Path $PSScriptRoot '..') assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $password = $null
    $secure.Dispose()
}
