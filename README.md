# eladmin modernized

[![CI](https://github.com/SovNodeAI/eladmin-modernized/actions/workflows/ci.yml/badge.svg)](https://github.com/SovNodeAI/eladmin-modernized/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-E76F00.svg)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

English | [简体中文](README.zh-CN.md)

An independently maintained Java 21 and Spring Boot 4.1.1 modernization of [eladmin](https://github.com/elunez/eladmin), with a checksummed 139-record controller census against upstream commit `55fbf705956949697dbd68bf9003776609d3d029`.

The goal is narrow and reviewable: retain the five-module backend and tracked endpoint declarations while migrating the runtime, Jakarta integrations, API documentation, configuration, and verification harness. This is not an official eladmin release and is not affiliated with the upstream website, demo, or community channels.

## Reproducible release evidence

The public verification gate requires:

- 185 behavioral tests
- 33 static migration-contract tests
- 1 migration snapshot
- zero failures, errors, and skipped tests
- real MySQL 8.4 and Redis 7.2 context startup through Testcontainers
- 139 endpoint census records matching byte for byte
- 141 OpenAPI operations and 414 schemas
- positive method-security checks and low-privilege denial controls
- dependency, checksum, provenance, and secret scans

The three test categories are reported separately; their aggregate is a CI inventory invariant, not a coverage claim. See the public [claims ledger](docs/verification/CLAIMS_LEDGER.md), [test taxonomy](docs/verification/TEST_TAXONOMY.md), [risk map](docs/verification/RISK_MAP.md), and [adversarial guard evidence](docs/verification/REFUTER_EVIDENCE.md).

## Modernized stack

| Area | Upstream baseline | This repository |
| --- | --- | --- |
| Java | 8 | 21 |
| Spring Boot | 2.7.18 | 4.1.1 |
| API documentation | Springfox | springdoc 3.1.0 |
| Enterprise APIs | Java EE namespaces | Jakarta EE namespaces |
| MapStruct | Legacy processor chain | 1.6.3 with explicit processor order |
| Database pool | Legacy Druid starter | Druid Spring Boot 4 starter |
| Integration tests | Environment-dependent | MySQL and Redis Testcontainers |

The modules remain `eladmin-common`, `eladmin-logging`, `eladmin-system`, `eladmin-tools`, and `eladmin-generator`.

## Specific security changes

- Production startup rejects missing database, Redis, JWT, RSA, and DES inputs.
- The Druid console and S3 integration are disabled by default; the console has no shipped login credential.
- Password hashes are excluded from login, `/auth/info`, and cache serialization responses.
- New-user and administrative password-reset operations generate random one-time passwords instead of `123456`.
- Storage, payment, mail, generator, and verification-code operations have explicit permission checks with low-privilege denial tests.
- Spreadsheet exports neutralize formula-leading text before writing XLSX cells.
- fastjson 1.x and Commons Configuration 1.x are banned from the complete Maven dependency graph.

These controls do not amount to a blanket security certification. DES/CBC remains as documented compatibility debt, and privileged remote-deployment and database-connection features still need deployment-specific isolation. See the [upstream CVE status table](docs/security/UPSTREAM_CVE_STATUS.md) and [`SECURITY.md`](SECURITY.md).

## Requirements

- JDK 21
- Maven 3.9 or newer
- MySQL 8
- Redis 7
- Docker for the complete verification suite

The application listens on port `8000` by default. Select a Spring profile explicitly; packaged builds do not silently select the development profile.

## Build and verify

```bash
mvn -B -ntp clean verify
java tools/endpoint-census/EndpointCensus.java . tools/endpoint-census/endpoint-census.txt verify
java tools/openapi-audit/OpenApiAudit.java .
```

The Maven gate uses Testcontainers and therefore requires Docker. GitHub Actions parses every Surefire report and rejects skipped, failed, missing, or unexpected tests.

## Local setup

Generate local credentials and initialization material:

```bash
bash tools/onboard-local.sh
```

The script writes generated values to ignored local files: `local.env`, `local-bootstrap.sql`, and `local_rsa_public.b64`.

Load the environment, initialize MySQL with `sql/eladmin.sql`, apply `local-bootstrap.sql`, and start the system module:

```bash
set -a
. ./local.env
set +a
mvn -pl eladmin-system -am spring-boot:run
```

Validate onboarding without starting the application:

```bash
bash tools/onboard-local.sh --self-test
```

## Production configuration

Set `SPRING_PROFILES_ACTIVE=prod` and provide:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PWD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_DB`, `REDIS_PWD`
- `JWT_SECRET`
- `RSA_PRIVATE_KEY`
- `ELADMIN_DES_KEY`

Optional integrations remain disabled until explicitly configured. Set `S3_ENABLED=true` only with dedicated `S3_ACCESS_KEY` and `S3_SECRET_KEY` values. Enable the Druid console only with separate credentials and a restricted allowlist.

## Frontend and behavior compatibility

The endpoint census preserves controller declarations, but several security-sensitive behaviors deliberately differ from upstream:

- Verification-code routes require `email:send`.
- User creation and password reset return a random password once as JSON.
- Seeded accounts require local onboarding instead of a shared password.

The stock [eladmin-web](https://github.com/elunez/eladmin-web) frontend does not render the one-time password response and may receive HTTP 403 in the email-change flow. Integrators must adapt those flows before deployment. See [`CHANGES.md`](CHANGES.md) for the complete behavior contract.

HTTP JSON and Redis values continue to use fastjson2 to preserve the characterized wire shape. These compatibility choices are pinned by executable tests, not presented as universal equivalence.

## Attribution and development disclosure

This repository is a derivative work of [elunez/eladmin](https://github.com/elunez/eladmin), originally authored by Zheng Jie and distributed under Apache License 2.0. Upstream notices are preserved, and SovNode’s modifications use the same license. See [`NOTICE`](NOTICE) and [`LICENSE`](LICENSE).

The modernization was implemented with AI-assisted engineering under human direction. Release claims are limited to reproducible public checks and independent review; deployment-specific testing and operator judgment remain necessary.

## Spring modernization services

SovNode helps teams move production Spring applications from Java 8, 11, or 17 and Spring Boot 2.x or 3.x to supported releases. Engagements can include compatibility assessment, implementation, security review, test recovery, CI modernization, frontend coordination, and runtime validation.

For a private migration assessment, contact **admin1@sovnode.ai**.
