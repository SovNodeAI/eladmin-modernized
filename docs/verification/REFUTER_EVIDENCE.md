# Adversarial guard evidence

The release relies on guards that can distinguish the intended implementation from a plausible regression. The probes below are represented by tracked tests or deterministic tools, so reviewers can inspect or repeat them without private logs.

| Claim under challenge | Discriminating guard | Expected failure when the control is removed |
| --- | --- | --- |
| Jakarta Mail is linked correctly | `EmailServiceImplBehaviorTest` sends through GreenMail | Restoring the legacy Hutool mail path produces a missing `javax.mail` linkage error |
| ERROR dispatches remain available to Spring error handling | `SecurityChainParityTest.errorDispatchStaysPermittedSoErrorPagesSurvive` | Removing the ERROR dispatcher permit returns the authentication-entry-point response instead of the error dispatch |
| Endpoint normalization is stable | Endpoint census verify mode plus checksum | Editing, deleting, or incorrectly joining a mapping changes the artifact and checksum |
| MapStruct sees Lombok accessors | Three mapper round-trip classes assert mapped fields and collections | Empty or partial generated mappers produce null or unequal fields |
| fastjson2 retains the selected HTTP shape | Exact converter output assertions | Removing a configured feature changes serialized bytes |
| Redis retains the selected value format | Unit and live serializer round trips | Type metadata loss or a converter mismatch prevents the asserted round trip |
| No module hides tests | `ReleaseIntegritySourceAuditTest` and workflow Surefire roll-up | A Surefire exclusion or skipped test fails the gate |
| Startup banner reports the resolved framework version | `StartupBannerVersionTest` compares against `SpringBootVersion.getVersion()` | Restoring a hard-coded upstream version fails the assertion |
| Low-privilege callers cannot use guarded management routes | Five negative-control classes invoke real method-security proxies | Removing a required `@PreAuthorize` changes the expected denial into an allowed call |
| Verification-code routes share the mail permission boundary | `VerifyLowPrivilegeNegativeControlTest` covers sending and validation | Removing `email:send` from a route makes the low-privilege control pass through |
| Legacy dependency bans are effective | Maven Enforcer and dependency graph contract tests | Reintroducing fastjson 1.x or Commons Configuration 1.x fails resolution-time verification |
| Generator seed data matches method security | `ShippedSecretsAbsentTest.seedSqlShipsNoCredentialAndGrantsGuardedMenus` | Removing any required permission row or seeded role grant fails the system test |

These probes support only the named claims. Residual risks remain documented in [`RISK_MAP.md`](RISK_MAP.md).
