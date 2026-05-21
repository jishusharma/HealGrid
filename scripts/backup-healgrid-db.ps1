param(
    [string]$ProjectDir = "D:\IdeaProjects\HealGrid",
    [string]$BackupDir = "D:\IdeaProjects\HealGrid\db-backups",
    [int]$KeepLast = 30
)

$ErrorActionPreference = "Stop"

# IntelliJ usage:
#   .\scripts\backup-healgrid-db.ps1
# Purpose:
#   Back up the local HealGrid Postgres evidence tables used for interview/debugging proof.
#   This includes both custom observability data and native Healenium healing data.

$tables = @(
    "healenium.selector",
    "healenium.healing",
    "healenium.healing_result",
    "healenium.report",
    "healenium.test_results"
)

function Assert-DockerReady {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    docker ps --format "{{.Names}}" | Out-Null
    $exitCode = $LASTEXITCODE
    if ($null -eq $exitCode) { $exitCode = 0 }
    $ErrorActionPreference = $oldPreference

    if ($exitCode -ne 0) {
        throw "Docker is not ready. Start Docker Desktop, then rerun this script from IntelliJ Terminal."
    }
}

function Wait-ForPostgres {
    for ($i = 1; $i -le 30; $i++) {
        $oldPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        docker exec healgrid-postgres-db-1 pg_isready -U healenium_user -d healenium 1>$null 2>$null
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = $oldPreference
        if ($exitCode -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Postgres did not become ready. Check docker-compose ps."
}

Assert-DockerReady
Set-Location -LiteralPath $ProjectDir

docker-compose up -d postgres-db | Out-Host
Wait-ForPostgres

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupFile = Join-Path $BackupDir "healgrid-healenium-evidence-$timestamp.sql"
$latestFile = Join-Path $BackupDir "latest-healenium-evidence.sql"

$tableArgs = @()
foreach ($table in $tables) {
    $tableArgs += "-t"
    $tableArgs += $table
}

& docker exec healgrid-postgres-db-1 pg_dump `
    -U healenium_user `
    -d healenium `
    -n healenium `
    @tableArgs `
    --no-owner `
    --no-privileges `
    --column-inserts |
    Set-Content -LiteralPath $backupFile -Encoding UTF8

Copy-Item -LiteralPath $backupFile -Destination $latestFile -Force

Get-ChildItem -LiteralPath $BackupDir -Filter "healgrid-healenium-evidence-*.sql" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip $KeepLast |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }

Write-Host ""
Write-Host "HealGrid DB evidence backup created: $backupFile"
Write-Host "Latest backup pointer: $latestFile"
Write-Host ""
Write-Host "Backed up tables:"
foreach ($table in $tables) {
    Write-Host "- $table"
}





