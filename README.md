# HealGrid Automation Framework

A Java test automation framework built around execution reliability and observability.
Covers API, Selenium Grid, and Mobile Web from a single codebase — with self-healing locators,
AI-driven failure classification, and historical test health tracking via PostgreSQL.

[![Build Status](https://canon-anymore-maturity.ngrok-free.dev/buildStatus/icon?job=HealGrid-Pipeline&ngrok-skip-browser-warning=true)](https://canon-anymore-maturity.ngrok-free.dev/job/HealGrid-Pipeline/)
![Java](https://img.shields.io/badge/Java-17-blue)
![Selenium](https://img.shields.io/badge/Selenium-4.11-green)
![Healenium](https://img.shields.io/badge/Healenium-3.5.1-purple)
![TestNG](https://img.shields.io/badge/TestNG-7.4-red)
![Docker](https://img.shields.io/badge/Docker-ready-blue)
![Grid](https://img.shields.io/badge/Selenium%20Grid-4.21-orange)

---

## Architecture

![Framework Architecture](docs/architecture.svg)

---

## Capabilities

- **ThreadLocal DriverFactory** — parallel-safe driver lifecycle with 4-level config fallback (JVM flag → env var → properties file → default)
- **Self-healing locators** — Healenium wraps the driver and recovers broken selectors automatically; healing history persisted in PostgreSQL
- **Three execution targets** — local JVM, Dockerized Selenium Grid (Chrome + Firefox nodes), BrowserStack cloud (Android + iOS)
- **AI-driven failure classification and selective rerun** — `AiFailureAnalyzer` classifies failures post-run via Groq API and reruns only transient failures in the same build, leaving genuine defects flagged
- **AI-based visual validation** — OpenCV compares screenshots against baselines to catch visual regressions that DOM assertions cannot detect
- **Flaky detection** — `FlakyDetector` queries the last 5 runs per test and classifies each as `STABLE` or `FLAKY`, excluding infra noise
- **Trend reporting** — `TrendReporter` computes suite pass rate across recent builds from PostgreSQL; no external analytics dependency
- **Full CI/CD pipeline** — Jenkins orchestration with suite-isolated stages, conditional mobile execution, reporting, and notifications

---

## Tech stack

| | |
|---|---|
| Language | Java 17 |
| Test framework | TestNG 7.4 |
| UI automation | Selenium WebDriver 4.11 |
| Self-healing | Healenium 3.5 |
| API testing | REST Assured 5.3 |
| Grid | Selenium Grid 4.21 (Docker) |
| Mobile cloud | BrowserStack (Android + iOS) |
| CI/CD | Jenkins LTS, Docker Compose |
| Observability | PostgreSQL, Allure 2.23 |
| AI analysis | Groq API, OpenCV 4.7 |
| Build | Maven 3.9 |

---

## Running the framework

**Prerequisites:** Java 17, Maven 3.9+, Docker Desktop

```bash
# API tests
mvn test -Dsurefire.suiteXmlFiles=testNgXmls/api.xml

# UI tests — parallel by class (local)
mvn test -Dsurefire.suiteXmlFiles=testNgXmls/parallelClasses.xml

# UI tests — cross-browser parallel
mvn test -Dsurefire.suiteXmlFiles=testNgXmls/multiBrowserClasses.xml

# Grid + Healenium (full Docker)
docker-compose up --build --abort-on-container-exit test-runner

# Mobile — BrowserStack
mvn test -Dsurefire.suiteXmlFiles=testNgXmls/mobile.xml \
  -Dexecution=browserstack -Dbs.device="Samsung Galaxy S23" -Dbs.os.version=13.0
```