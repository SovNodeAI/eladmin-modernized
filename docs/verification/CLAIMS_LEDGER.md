# Public claims ledger

This ledger limits release claims to facts a reviewer can reproduce from this repository. A source assertion is not treated as runtime evidence, and the test total is not presented as coverage.

| Claim | Public evidence | Reproduction |
| --- | --- | --- |
| The project builds for Java 21 on Spring Boot 4.1.1 | Root [`pom.xml`](../../pom.xml), module poms, and the startup banner test | `mvn -B -ntp clean verify` |
| The backend keeps the five upstream modules | Root reactor module list | Inspect `pom.xml` or run `mvn help:effective-pom` |
| The controller inventory contains 139 endpoint records | Checksummed [`endpoint-census.txt`](../../tools/endpoint-census/endpoint-census.txt) and its verifier | `java tools/endpoint-census/EndpointCensus.java . tools/endpoint-census/endpoint-census.txt verify` |
| The migrated OpenAPI model contains 141 operations and 414 schemas | Source-driven OpenAPI audit | `java tools/openapi-audit/OpenApiAudit.java .` |
| The suite contains 185 behavioral tests, 33 static contract tests, and 1 migration snapshot | Tagged test sources and [`TEST_TAXONOMY.md`](TEST_TAXONOMY.md) | Run the Maven gate and inspect `target/surefire-reports` |
| Docker-backed application contexts start with MySQL 8.4 and Redis 7.2 | `EladminSystemApplicationTests` and `ProdProfileContextBootTests` use Testcontainers | The complete Maven gate must execute them with zero skips |
| Password hashes are not serialized by the login or `/auth/info` responses | `UserDto` and `JwtUserDto` serialization controls plus `UserPasswordNonSerializationTest` | Run the system module tests or the complete gate |
| Low-privilege callers are denied on storage, payment, mail, generator, and verification operations | Five low-privilege negative-control classes and method-security tests | Run the complete Maven gate; the tests assert HTTP 403 and the response shape |
| Production credentials are not shipped as usable defaults | Environment placeholders, fail-closed startup guard, seed SQL contract, and secret scan | Run `ShippedSecretsAbsentTest`, `BootstrapFailClosedTest`, and the workflow secret scan |
| Spreadsheet exports neutralize formula-leading text | `FileUtil.sanitizeCellValue` and `FileUtilTest` | Run `mvn -pl eladmin-common test` |
| The mail path uses Jakarta Mail and delivers through a real SMTP test server | `EmailServiceImplBehaviorTest` with GreenMail | Run `mvn -pl eladmin-tools -am test` |
| MapStruct-generated mappers retain field behavior on Java 21 | Mapper round-trip tests in logging, system, and tools | Run the complete Maven gate |
| Legacy fastjson 1.x and Commons Configuration 1.x are absent from the runtime graph | Maven Enforcer bans and dependency contract tests | Run `mvn -B -ntp verify` and inspect dependency trees |
| HTTP and Redis fastjson2 compatibility choices are pinned | Converter and Redis round-trip characterization tests | Run the common and system tests |

## Claim discipline

- “Endpoint preserving” refers to the tracked 139-record controller census against the stated upstream baseline. It does not promise identical database state or UI behavior.
- “Verified” means the public workflow ran the cited checks. It does not mean every possible production integration was exercised.
- Security statements name specific controls. The project does not claim a complete security audit or blanket production readiness.
- Compatibility exceptions and unresolved risks remain visible in [`RISK_MAP.md`](RISK_MAP.md) and [`../security/UPSTREAM_CVE_STATUS.md`](../security/UPSTREAM_CVE_STATUS.md).
