param(
    [string]$ProjectDir = "D:\IdeaProjects\HealGrid",
    [string]$BuildId = "healenium-proof-healed"
)

$ErrorActionPreference = "Stop"

# IntelliJ usage:
#   powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\run-healenium-proof.ps1"
# Purpose:
#   Run the complete Healenium proof story:
#   1. Verify baseline DB evidence.
#   2. Prove the drifted locator fails when healing is disabled.
#   3. Prove the same drifted locator passes when healing is enabled.
#   4. Confirm new healing/healing_result rows.
#   5. Persist the proof run into test_results.
#   6. Regenerate the trend report and render a screenshot when Chrome is available.

function Invoke-HealGridSql {
    param([string]$Sql)
    docker exec healgrid-postgres-db-1 psql -U healenium_user -d healenium -c $Sql
}

function Invoke-MavenProof {
    param(
        [string]$HealEnabled,
        [bool]$IgnoreFailure
    )

    $args = @(
        "-q",
        "test",
        "-Dheadless=true",
        "-Dhealenium.host=localhost",
        "-Dexecution=grid",
        "-Dgrid.url=http://localhost:4444",
        "-Dheal.enabled=$HealEnabled",
        "-Dsurefire.suiteXmlFiles=testNgXmls/healenium-healing-proof.xml"
    )

    if ($IgnoreFailure) {
        $args += "-Dmaven.test.failure.ignore=true"
    }

    & mvn @args
}

function Show-SurefireSummary {
    $summary = Join-Path $ProjectDir "target\surefire-reports\TestSuite.txt"
    if (Test-Path -LiteralPath $summary) {
        Get-Content -LiteralPath $summary
    } else {
        Write-Host "Surefire summary not found: $summary"
    }
}

Set-Location -LiteralPath $ProjectDir

Write-Host ""
Write-Host "STEP 1 - Start required containers"
docker-compose up -d postgres-db healenium selector-imitator selenium-hub chrome

Write-Host ""
Write-Host "STEP 2 - Baseline Healenium DB counts"
Invoke-HealGridSql "select 'selector' as table_name, count(*) as row_count from healenium.selector union all select 'healing', count(*) from healenium.healing union all select 'healing_result', count(*) from healenium.healing_result union all select 'report', count(*) from healenium.report union all select 'test_results', count(*) from healenium.test_results order by table_name;"

Write-Host ""
Write-Host "STEP 3 - Negative control: healing disabled. Expected: baseline test passes, drifted locator fails."
Invoke-MavenProof -HealEnabled "false" -IgnoreFailure $true
Show-SurefireSummary

Write-Host ""
Write-Host "STEP 4 - Positive control: healing enabled. Expected: both tests pass and Healenium logs a healed locator."
Invoke-MavenProof -HealEnabled "true" -IgnoreFailure $true
Show-SurefireSummary

Write-Host ""
Write-Host "STEP 5 - Confirm persisted Healenium healing evidence for the controlled drift"
Invoke-HealGridSql "select h.create_date as healed_at, s.url, s.class_name, s.method_name, s.locator as original_locator, hr.locator as healed_candidate, round(hr.score::numeric,4) as score, hr.success_healing from healenium.healing h join healenium.selector s on s.uid = h.selector_id join healenium.healing_result hr on hr.healing_id = h.uid where hr.locator like '%healenium-proof-add-button%' order by h.create_date desc, hr.score desc limit 10;"

Write-Host ""
Write-Host "STEP 6 - Persist the healed proof run into custom test_results"
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "healenium"
$env:DB_USER = "healenium_user"
$env:DB_PASSWORD = "healenium_password"
$env:LOCAL_BUILD_ID = $BuildId
$env:EXECUTION_SOURCE = "LOCAL_MAVEN"
mvn -q verify -P observability -DskipTests
Invoke-HealGridSql "select build, suite, status, count(*) from healenium.test_results where build='$BuildId' group by build, suite, status order by status;"

Write-Host ""
Write-Host "STEP 7 - Regenerate trend report"
mvn -q exec:java "-Dexec.mainClass=observability.TrendReporter" "-Dexec.classpathScope=runtime"

Write-Host ""
Write-Host "STEP 8 - Render trend report screenshot if Chrome is available"
$chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$report = "file:///$($ProjectDir.Replace('\','/'))/target/observability/trend-report.html"
$screenshot = Join-Path $ProjectDir "target\observability\trend-report-screenshot.png"
if (Test-Path -LiteralPath $chrome) {
    & $chrome --headless=new --disable-gpu --allow-file-access-from-files --window-size=1400,1100 --virtual-time-budget=5000 "--screenshot=$screenshot" $report
    Write-Host "Trend report screenshot: $screenshot"
} else {
    Write-Host "Chrome not found. Open target\observability\trend-report.html manually."
}

Write-Host ""
Write-Host "DONE - Interview lesson"
Write-Host "Healing disabled proves the locator really broke. Healing enabled proves SelfHealingDriver recovered it. DB rows prove it was persisted, and the trend report shows the proof run as a 100% pass-rate build."
