# eladmin modernized

[![CI](https://github.com/SovNodeAI/eladmin-modernized/actions/workflows/ci.yml/badge.svg)](https://github.com/SovNodeAI/eladmin-modernized/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-E76F00.svg)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

An independently maintained Java 21 and Spring Boot 4.1.1 modernization of [eladmin](https://github.com/elunez/eladmin).

This release preserves eladmin's five-module backend structure while modernizing its runtime, framework integrations, security boundaries, configuration, and verification suite. It is not an official eladmin release and is not affiliated with the upstream website, demo, or community channels.

## Release evidence

The publication candidate was verified on August 31, 2026 with:

- 216 tests executed
- 0 failures
- 0 errors
- 0 skipped tests
- 139 endpoint census records
- 141 OpenAPI operations and 414 schemas
- positive and negative authorization controls
- real MySQL and Redis context startup through Testcontainers
- browser and API smoke verification
- source linkage, checksum, and secret review gates

Every public push runs the complete Maven lifecycle with Docker-backed Testcontainers, verifies the endpoint census and OpenAPI audit, and rejects any skipped, failed, or missing tests.

The detailed claims ledger, test taxonomy, risk map, and adversarial review evidence are retained in [`docs/verification`](docs/verification/README.md).

## Modernized stack

| Area | Upstream baseline | This release |
| --- | --- | --- |
| Java | 8 | 21 |
| Spring Boot | 2.7.18 | 4.1.1 |
| API documentation | Springfox | springdoc 3.1.0 |
| Jakarta migration | Java EE namespaces | Jakarta EE namespaces |
| MapStruct | Legacy processor chain | 1.6.3 with explicit processor order |
| Database pool | Legacy Druid starter | Druid Spring Boot 4 starter |
| Integration testing | Partial and environment-dependent | MySQL 8.4 and Redis 7.2 Testcontainers |

The project modules remain:

- `eladmin-common`
- `eladmin-logging`
- `eladmin-system`
- `eladmin-tools`
- `eladmin-generator`

## Security changes

- Production startup fails closed when required credentials are absent or invalid.
- Database, Redis, JWT, RSA, DES, S3, mail, and payment secrets are no longer shipped as usable repository values.
- S3 is disabled by default and validates its configuration at startup when enabled.
- New-user and password-reset credentials are generated randomly and returned once.
- Administrative storage, payment, mail, generator, and verification routes have explicit authorization controls.
- Low-privilege negative tests verify denial behavior and the standard HTTP 403 response shape.

See [`SECURITY.md`](SECURITY.md) for private vulnerability reporting.

## Requirements

- JDK 21
- Maven 3.9 or newer
- MySQL 8
- Redis 7
- Docker when running the complete integration suite

The application listens on port `8000` by default. Select a Spring profile explicitly; packaged builds do not silently select the development profile.

## Build and verify

```bash
mvn -B -ntp clean verify
java tools/endpoint-census/EndpointCensus.java . tools/endpoint-census/endpoint-census.txt verify
java tools/openapi-audit/OpenApiAudit.java .
```

The complete Maven verification uses Testcontainers and therefore requires Docker. CI asserts the exact expected test count and fails if any test is skipped.

## Local setup

Generate local credentials and initialization material:

```bash
bash tools/onboard-local.sh
```

The script writes generated values to ignored local files:

- `local.env`
- `local-bootstrap.sql`
- `local_rsa_public.b64`

Load the generated environment, initialize the database with `sql/eladmin.sql`, then apply `local-bootstrap.sql` before starting the application.

```bash
set -a
. ./local.env
set +a
mvn -pl eladmin-system -am spring-boot:run
```

Validate the onboarding path without starting the application:

```bash
bash tools/onboard-local.sh --self-test
```

## Production configuration

Set `SPRING_PROFILES_ACTIVE=prod` and provide the required environment variables:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PWD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_DB`, `REDIS_PWD`
- `JWT_SECRET`
- `RSA_PRIVATE_KEY`
- `ELADMIN_DES_KEY`

Optional integrations remain disabled until explicitly configured. Set `S3_ENABLED=true` only when `S3_ACCESS_KEY` and `S3_SECRET_KEY` are available. The Druid console is disabled by default and requires dedicated credentials when enabled.

## Compatibility notes

HTTP JSON and Redis value serialization continue to use fastjson2 to preserve the existing wire shape. Characterization tests pin date formatting, long-number precision, null handling, and reference behavior.

Several inherited dependencies remain under documented risk acceptance because replacing them would change application behavior. The rationale and verification for each accepted dependency are recorded in [`docs/verification/RISK_MAP.md`](docs/verification/RISK_MAP.md) and [`docs/verification/CLAIMS_LEDGER.md`](docs/verification/CLAIMS_LEDGER.md).

## Attribution

This repository is a derivative work of [elunez/eladmin](https://github.com/elunez/eladmin), originally authored by Zheng Jie and distributed under the Apache License 2.0. Upstream copyright notices are preserved. SovNode's modifications are also distributed under Apache License 2.0.

See [`NOTICE`](NOTICE) and [`LICENSE`](LICENSE) for complete attribution and license terms.

## Spring modernization services

SovNode helps teams migrate production Spring applications from Java 8, 11, or 17 and Spring Boot 2.x or 3.x to current supported releases. Engagements can include compatibility assessment, implementation, security review, test recovery, CI modernization, and runtime validation.

Contact **admin1@sovnode.ai** for a private migration assessment.
