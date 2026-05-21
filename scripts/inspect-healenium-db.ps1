param(
    [string]$ProjectDir = "D:\IdeaProjects\HealGrid"
)

$ErrorActionPreference = "Stop"

# IntelliJ usage:
#   .\scripts\inspect-healenium-db.ps1
# Purpose:
#   Print the small set of DB facts needed to debug and explain whether Healenium is learning,
#   reporting, and actually healing locators.

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

function Invoke-HealGridSql {
    param([string]$Sql)
    docker exec healgrid-postgres-db-1 psql -U healenium_user -d healenium -c $Sql
}

Assert-DockerReady
Set-Location -LiteralPath $ProjectDir

docker-compose up -d postgres-db | Out-Host

Write-Host ""
Write-Host "=== Docker services ==="
docker-compose ps

Write-Host ""
Write-Host "=== Healenium schema tables ==="
Invoke-HealGridSql "select table_schema, table_name from information_schema.tables where table_schema='healenium' order by table_name;"

Write-Host ""
Write-Host "=== Evidence table row counts ==="
Invoke-HealGridSql "select 'selector' as table_name, count(*) as row_count from healenium.selector union all select 'healing', count(*) from healenium.healing union all select 'healing_result', count(*) from healenium.healing_result union all select 'report', count(*) from healenium.report union all select 'test_results', count(*) from healenium.test_results order by table_name;"

Write-Host ""
Write-Host "=== What Healenium actually healed ==="
Invoke-HealGridSql "select s.url, s.method_name, s.locator as original_locator, count(distinct h.uid) as healing_attempts, count(hr.id) as candidate_count, round(max(hr.score)::numeric,4) as max_score, bool_or(hr.success_healing) as had_success from healenium.healing h join healenium.selector s on s.uid=h.selector_id join healenium.healing_result hr on hr.healing_id=h.uid group by s.url, s.method_name, s.locator order by healing_attempts desc, max_score desc;"

Write-Host ""
Write-Host "=== Healing activity by day ==="
Invoke-HealGridSql "select date(create_date) as day, count(*) as healing_attempts from healenium.healing group by date(create_date) order by day; select date(create_date) as day, success_healing, count(*) as candidate_count from healenium.healing_result group by date(create_date), success_healing order by day, success_healing;"

Write-Host ""
Write-Host "Debugging lesson: selector/report rows prove Healenium is collecting data. healing/healing_result rows prove actual healing attempts and candidate recovery."





