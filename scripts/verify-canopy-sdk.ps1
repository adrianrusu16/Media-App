[CmdletBinding()]
param(
    [string]$RepositoryRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
}

$expectedRelease = 'v0.2.0'
$expectedCommit = '145678c1d73e45b7bbaebf7e16ee4d64'
$expectedProstPackage = 'pandawave_canopy-api_community_neoeinstein-prost'
$expectedTonicPackage = 'pandawave_canopy-api_community_neoeinstein-tonic'
$expectedProst = '=0.5.0-00000000000000-145678c1d73e.2'
$expectedTonic = '=0.5.0-00000000000000-145678c1d73e.4'

function Fail-Verification {
    param([Parameter(Mandatory = $true)][string]$Message)

    [Console]::Error.WriteLine("Canopy SDK compatibility check failed: $Message")
    exit 1
}

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [AllowNull()][object]$Actual,
        [AllowNull()][object]$Expected
    )

    if ([string]$Actual -cne [string]$Expected) {
        Fail-Verification "$Label is '$Actual'; expected '$Expected'."
    }
}

function Get-SdkCommitFragment {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$VersionRequirement
    )

    $match = [regex]::Match(
        $VersionRequirement,
        '^=[0-9]+\.[0-9]+\.[0-9]+-[0-9]{14}-(?<commit>[0-9a-f]{12})\.[0-9]+$'
    )
    if (-not $match.Success) {
        Fail-Verification "$Label must be one exact immutable BSR version, not '$VersionRequirement'."
    }

    return $match.Groups['commit'].Value
}

try {
    $repository = (Resolve-Path -LiteralPath $RepositoryRoot).Path
} catch {
    Fail-Verification "repository root '$RepositoryRoot' does not exist."
}

$engineRoot = Join-Path $repository 'rust/engine'
$workspaceManifest = Join-Path $engineRoot 'Cargo.toml'
$lockFile = Join-Path $engineRoot 'Cargo.lock'
if (-not (Test-Path -LiteralPath $workspaceManifest -PathType Leaf)) {
    Fail-Verification "missing workspace manifest: $workspaceManifest"
}
if (-not (Test-Path -LiteralPath $lockFile -PathType Leaf)) {
    Fail-Verification "missing lock file: $lockFile"
}

Push-Location $engineRoot
try {
    $metadataJson = & cargo metadata --format-version 1 --locked
    if ($LASTEXITCODE -ne 0) {
        Fail-Verification "cargo metadata could not parse the locked workspace (exit $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

try {
    $metadata = $metadataJson | ConvertFrom-Json
} catch {
    Fail-Verification "cargo metadata returned invalid JSON: $($_.Exception.Message)"
}

$corePackages = @($metadata.packages | Where-Object { $_.name -ceq 'panda_engine_core' })
if ($corePackages.Count -ne 1) {
    Fail-Verification "expected one panda_engine_core workspace package; found $($corePackages.Count)."
}
$corePackage = $corePackages[0]

function Get-DeclaredDependency {
    param(
        [Parameter(Mandatory = $true)][string]$Alias,
        [Parameter(Mandatory = $true)][string]$PackageName
    )

    $dependencies = @($corePackage.dependencies | Where-Object {
        $_.rename -ceq $Alias -and $_.name -ceq $PackageName -and $null -eq $_.kind
    })
    if ($dependencies.Count -ne 1) {
        Fail-Verification "expected exactly one normal dependency '$Alias' for package '$PackageName'; found $($dependencies.Count)."
    }
    return $dependencies[0]
}

$prostDependency = Get-DeclaredDependency 'canopy-api-prost' $expectedProstPackage
$tonicDependency = Get-DeclaredDependency 'canopy-api-tonic' $expectedTonicPackage
Assert-Equal 'declared Prost requirement' $prostDependency.req $expectedProst
Assert-Equal 'declared Tonic requirement' $tonicDependency.req $expectedTonic

$expectedRegistry = 'sparse+https://buf.build/gen/cargo/'
Assert-Equal 'Prost registry' $prostDependency.registry $expectedRegistry
Assert-Equal 'Tonic registry' $tonicDependency.registry $expectedRegistry

$prostCommit = Get-SdkCommitFragment 'Prost requirement' $prostDependency.req
$tonicCommit = Get-SdkCommitFragment 'Tonic requirement' $tonicDependency.req
$expectedCommitFragment = $expectedCommit.Substring(0, 12)
Assert-Equal 'Prost BSR commit fragment' $prostCommit $expectedCommitFragment
Assert-Equal 'Tonic BSR commit fragment' $tonicCommit $expectedCommitFragment
Assert-Equal 'Prost/Tonic BSR commit fragments' $prostCommit $tonicCommit

function Assert-ResolvedPackage {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$PackageName,
        [Parameter(Mandatory = $true)][string]$ExpectedVersion
    )

    $resolved = @($metadata.packages | Where-Object {
        $_.name -ceq $PackageName -and $_.source -ceq $expectedRegistry
    })
    if ($resolved.Count -ne 1) {
        Fail-Verification "expected one resolved $Label package '$PackageName' from BSR; found $($resolved.Count)."
    }
    Assert-Equal "resolved $Label version" $resolved[0].version $ExpectedVersion.TrimStart('=')
}

Assert-ResolvedPackage 'Prost' $expectedProstPackage $expectedProst
Assert-ResolvedPackage 'Tonic' $expectedTonicPackage $expectedTonic

function Get-RepositoryFiles {
    param(
        [Parameter(Mandatory = $true)][string[]]$RelativeRoots,
        [Parameter(Mandatory = $true)][string[]]$ExcludedDirectoryNames
    )

    $files = New-Object 'System.Collections.Generic.List[System.IO.FileInfo]'
    foreach ($relativeRoot in $RelativeRoots) {
        $scanRoot = Join-Path $repository $relativeRoot
        if (-not (Test-Path -LiteralPath $scanRoot -PathType Container)) {
            continue
        }

        $pendingDirectories = New-Object System.Collections.Stack
        $pendingDirectories.Push((Get-Item -LiteralPath $scanRoot))
        while ($pendingDirectories.Count -gt 0) {
            $directory = $pendingDirectories.Pop()
            foreach ($child in @(Get-ChildItem -Force -LiteralPath $directory.FullName)) {
                if ($child.PSIsContainer) {
                    if ($ExcludedDirectoryNames -cnotcontains $child.Name.ToLowerInvariant()) {
                        $pendingDirectories.Push($child)
                    }
                } else {
                    $files.Add($child)
                }
            }
        }
    }

    return $files
}

function Get-RepositoryRelativePath {
    param([Parameter(Mandatory = $true)][string]$FullName)

    return $FullName.Substring($repository.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
}

$generatedDirectoryNames = @(
    '.cache',
    '.git',
    '.gradle',
    '.idea',
    '.kotlin',
    'build',
    'dist',
    'generated',
    'graphify-out',
    'node_modules',
    'out',
    'target',
    'target-codex'
)
$artifactFiles = @(Get-RepositoryFiles @('app', 'core', 'feature') $generatedDirectoryNames | Where-Object {
    $_.Name -ieq 'client-connection.json'
})
$artifactRelativePaths = @($artifactFiles | ForEach-Object {
    Get-RepositoryRelativePath $_.FullName
} | Sort-Object -Unique)
if ($artifactRelativePaths.Count -eq 0) {
    Fail-Verification 'no shipped client-connection.json artifacts were found under app, core, or feature.'
}

foreach ($relativePath in $artifactRelativePaths) {
    $artifactPath = Join-Path $repository $relativePath
    if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
        Fail-Verification "missing shipped connection artifact: $relativePath"
    }
    try {
        $artifact = Get-Content -Raw -LiteralPath $artifactPath | ConvertFrom-Json
    } catch {
        Fail-Verification "$relativePath is not valid JSON: $($_.Exception.Message)"
    }

    Assert-Equal "$relativePath protobuf package" $artifact.contract.protobuf_package 'canopy.v1'
    Assert-Equal "$relativePath BSR module" $artifact.contract.bsr_module 'buf.build/pandawave/canopy-api'
    Assert-Equal "$relativePath documented release" $artifact.contract.release $expectedRelease
    Assert-Equal "$relativePath immutable commit" $artifact.contract.commit $expectedCommit
    Assert-Equal "$relativePath Prost package" $artifact.contract.prost_package $expectedProstPackage
    Assert-Equal "$relativePath Prost version" $artifact.contract.prost_version $expectedProst
    Assert-Equal "$relativePath Tonic package" $artifact.contract.tonic_package $expectedTonicPackage
    Assert-Equal "$relativePath Tonic version" $artifact.contract.tonic_version $expectedTonic
}

$forbiddenBuildPackages = @(
    'prost-build',
    'tonic-build',
    'protobuf-codegen',
    'protobuf-codegen-pure',
    'openapi-generator',
    'openapi-generator-cli'
)
foreach ($workspacePackageId in @($metadata.workspace_members)) {
    $workspacePackage = @($metadata.packages | Where-Object { $_.id -ceq $workspacePackageId })
    if ($workspacePackage.Count -ne 1) {
        Fail-Verification "cargo metadata did not describe workspace member '$workspacePackageId'."
    }
    $forbiddenDependencies = @($workspacePackage[0].dependencies | Where-Object {
        $forbiddenBuildPackages -ccontains $_.name
    })
    if ($forbiddenDependencies.Count -gt 0) {
        $names = ($forbiddenDependencies | ForEach-Object { $_.name } | Sort-Object -Unique) -join ', '
        Fail-Verification "workspace package '$($workspacePackage[0].name)' reintroduces local SDK generation dependencies: $names."
    }
}

$productionExcludedDirectoryNames = @($generatedDirectoryNames) + @(
    '.codex',
    '.serena',
    '.superpowers',
    'androidtest',
    'docs',
    'test',
    'testdata',
    'tests',
    'testing'
)
$sourceFiles = @(Get-RepositoryFiles @('.') $productionExcludedDirectoryNames | Where-Object {
    $_.FullName -cne $PSCommandPath
} | Sort-Object FullName)

$localProto = @($sourceFiles | Where-Object { $_.Extension -ieq '.proto' })
if ($localProto.Count -gt 0) {
    $relativeProto = Get-RepositoryRelativePath $localProto[0].FullName
    Fail-Verification "local protobuf inputs are forbidden; use the immutable BSR packages: $relativeProto"
}

$openApiGeneratorConfig = @($sourceFiles | Where-Object {
    $_.Name -match '(?i)(openapi-generator|swagger-codegen|oapi-codegen|buf\.gen\.)'
})
if ($openApiGeneratorConfig.Count -gt 0) {
    $relativeConfig = Get-RepositoryRelativePath $openApiGeneratorConfig[0].FullName
    Fail-Verification "client generation configuration is forbidden: $relativeConfig"
}

$scannableExtensions = @(
    '.bat',
    '.cmd',
    '.gradle',
    '.groovy',
    '.java',
    '.json',
    '.kt',
    '.kts',
    '.ps1',
    '.py',
    '.rs',
    '.sh',
    '.toml',
    '.yaml',
    '.yml'
)
$generationPattern = '(?i)(tonic[_-]build|prost[_-]build|compile_protos|include_proto!|buf\s+generate|protoc\b|openapi-generator|swagger-codegen|oapi-codegen|openapi[^\r\n]{0,80}(?:generate|codegen)|(?:generate|codegen)[^\r\n]{0,80}openapi)'
foreach ($sourceFile in @($sourceFiles | Where-Object {
    [string]::IsNullOrEmpty($_.Extension) -or $scannableExtensions -ccontains $_.Extension.ToLowerInvariant()
})) {
    $sourceText = Get-Content -Raw -LiteralPath $sourceFile.FullName
    if ($sourceText -match $generationPattern) {
        $relativeSource = Get-RepositoryRelativePath $sourceFile.FullName
        Fail-Verification "local protobuf or OpenAPI client generation is forbidden: $relativeSource"
    }
}

Write-Host 'Canopy SDK compatibility check PASS'
Write-Host "  release: $expectedRelease"
Write-Host "  commit:  $expectedCommit"
Write-Host "  Prost:   $expectedProstPackage $expectedProst"
Write-Host "  Tonic:   $expectedTonicPackage $expectedTonic"
Write-Host "  artifacts: $($artifactRelativePaths -join ', ')"
