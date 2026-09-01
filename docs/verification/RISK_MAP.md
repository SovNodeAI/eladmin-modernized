# Migration risk map

This table separates verified controls from residual risks. “Closed” means the named regression path is covered; it is not a whole-system security certification.

| Area | Failure mode | Public guard | Status |
| --- | --- | --- | --- |
| Application startup | Framework or Jakarta linkage fails only at runtime | MySQL and Redis Testcontainers context tests | Closed for the tested profiles |
| Authorization | A guarded management route becomes available to a low-privilege account | Positive method-security tests and low-privilege negative controls | Closed for the enumerated routes |
| Endpoint migration | Controller paths are lost, joined incorrectly, or invented | Checksummed 139-record endpoint census | Closed for controller declarations |
| OpenAPI migration | Springfox removal loses operations or schemas | Source-driven audit expecting 141 operations and 414 schemas | Closed for the tracked model |
| Mail | A remaining `javax.mail` linkage fails on first send | Jakarta Mail implementation and GreenMail delivery test | Closed |
| MapStruct | Java 21 annotation processing emits compilable but empty mappers | Field-level round-trip tests in three modules | Closed |
| Redis compatibility | Serializer migration breaks existing value shape | Unit and live round-trip tests | Closed for characterized values |
| HTTP JSON compatibility | Framework 7 or fastjson2 changes dates, longs, nulls, or references | Byte-shape characterization test | Closed for characterized values; monitor upgrades |
| Dependency graph | Vulnerable fastjson 1.x or Commons Configuration 1.x returns transitively | Maven Enforcer bans and classpath checks | Closed for the current graph |
| Spreadsheet exports | Formula-leading text is interpreted by spreadsheet software | Central cell sanitizer and regression tests | Closed for exports using `FileUtil.downloadExcel` |
| Generator seed data | UI roles lack permissions now required by controller guards | Seed SQL contract requiring five generator permissions and both seeded role assignments | Closed |
| Production configuration | Repository defaults expose usable credentials | Environment placeholders, disabled Druid console, onboarding script, secret scan | Closed for tracked files |
| DES-protected legacy values | DES/CBC remains weak even though the hardcoded key was removed | Required external key and fail-closed validation | Open compatibility debt; migrate stored values before production use |
| Remote deployment | Shell-oriented deployment remains a high-impact privileged feature | Existing permission checks only | Residual risk; isolate or disable unless required |
| Database connection testing | A privileged user can direct outbound database connections | Explicit `database:testConnect` permission | Residual SSRF-style risk; network egress controls recommended |
| Quartz execution | Scheduled job failures and unsafe custom jobs are deployment-specific | No dedicated end-to-end gate | Open, accepted for this release |
| Stock Vue frontend | New one-time-password and email permission behavior is not rendered by the unchanged frontend | Documented integration contract in [`CHANGES.md`](../../CHANGES.md) | Requires frontend adaptation |
