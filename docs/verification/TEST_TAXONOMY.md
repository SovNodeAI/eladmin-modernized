# Test taxonomy

Every authored test class carries exactly one class level JUnit `@Tag`
declaring what kind of evidence it is. Counts in handoffs must be
reported per category; the sum is never behavioral coverage.

The class census measured for this inventory is 43 classes: 36 tagged
`behavioral`, 6 `static-contract`, 1 `snapshot`. Fourteen classes use
the fully qualified `@org.junit.jupiter.api.Tag("...")` form and twenty
nine use the short `@Tag("...")` form; both spellings are the same
annotation. Audit tags with a recipe that accepts both forms and
anchors the match at line start:

    grep -rlE '^[[:space:]]*@(org\.junit\.jupiter\.api\.)?Tag\("' --include='*.java' eladmin-*/src/test | wc -l

which returns 43. Do not audit with `grep '@Tag('`: it misses the
qualified form and false-matches the springdoc template strings
asserted in `GeneratorTemplateContractTest` and
`OpenApiMigrationContractTest` (`@Tag(name = "${apiAlias}")`). An
earlier review using that substring recipe mis-derived 12 of 24 classes
as untagged; re-derivation with the recipe above shows every class
tagged. No tags were added to manufacture this result.

## Categories

- `behavioral`: executes production code and asserts observable
  outcomes (round trips, delivered mail, filter chain decisions,
  context boot). These are the only tests that can catch runtime
  linkage breaks, silent mapping loss, or converter drift.
- `static-contract`: text or model assertions over sources, poms, and
  YAML. Migration scaffolding that pins decisions; each class must
  disclose in its javadoc that it never boots anything. Planned
  deletion: remove or fold into the census tooling after the migration
  is accepted.
- `snapshot`: pins literal strings of a prior change. The
  anti-self-reference rule below governs new ones.

## Current inventory (SOV-346 integrated correction head)

| Module | behavioral | static-contract | snapshot |
|---|---|---|---|
| eladmin-common | AnonTagUtilsTest 2, AsyncExecutorLifecycleTest 1, DateUtilsTest 3, EncryptUtilsTest 3, FastJsonConverterShapeCharacterizationTest 3, FastJsonRedisSerializerRoundTripTest 3, FileUtilTest 4, GlobalExceptionHandlerAccessDeniedTest 5, StringUtilsTest 5 (29) | 0 | 0 |
| eladmin-logging | LogErrorMapperRoundTripTest 4 (4) | 0 | 0 |
| eladmin-generator | ColUtilTest 3, GenConfigLowPrivilegeNegativeControlTest 3, GeneratorLowPrivilegeNegativeControlTest 9, GeneratorMethodSecurityTest 9 (24) | GeneratorTemplateContractTest 5, OpenApiMigrationContractTest 7 (12) | NativeCountTypingContractTest 1 (1) |
| eladmin-tools | AlipayDependencyGraphContractTest 3, AlipaySdkCompatibilitySmokeTest 1, AliPayLowPrivilegeNegativeControlTest 5, AliPayMethodSecurityTest 15, AmzS3OptInContractTest 7, EmailLowPrivilegeNegativeControlTest 4, EmailMethodSecurityTest 13, EmailServiceImplBehaviorTest 1, LocalStorageLowPrivilegeNegativeControlTest 3, LocalStorageMapperRoundTripTest 4, LocalStorageMethodSecurityTest 6, S3StorageMethodSecurityTest 8 (70) | 0 | 0 |
| eladmin-system | BootstrapFailClosedTest 5, EladminSystemApplicationTests 3, ProdProfileContextBootTests 4, SecurityChainParityTest 23, StartupBannerVersionTest 1, TokenProviderRoundTripTest 4, UserControllerResetPasswordAuditTest 4, UserMapperRoundTripTest 4, UserPasswordNonSerializationTest 6, VerifyLowPrivilegeNegativeControlTest 4 (58) | DatasourceContractTest 9, RedisRuntimeContractTest 3, ReleaseIntegritySourceAuditTest 7, ShippedSecretsAbsentTest 2 (21) | 0 |
| total | 185 | 33 | 1 |

Method totals: 185 behavioral, 33 static-contract, 1 snapshot, which is
219 `@Test` methods across 43 classes. Class totals: 36 / 6 / 1. Every
per-class method count in the table was re-derived from the sources
with `grep -cE '^[[:space:]]*@Test[[:space:]]*$'` per file. The public
GitHub Actions gate requires the Surefire roll-up over
`**/target/surefire-reports/TEST-*.xml` to state reports=43 tests=219
failures=0 errors=0 skipped=0 after a Docker-backed `mvn -B -ntp clean
verify`. The five `*LowPrivilegeNegativeControlTest` classes are the
SOV-1322 low privilege negative control battery: 24 behavioral methods
that run without Docker or Redis, fail on the pre fix base for the
authorization reason only, and require the guarded routes plus the 403
denial mapping to pass. The `*MethodSecurityTest` classes carry the
method level security, sentinel mask round trip, and 403 denial shape
coverage for the surviving routes in the closure table.

The container boot slices `EladminSystemApplicationTests` and
`ProdProfileContextBootTests` skip honestly via
`disabledWithoutDocker` only on machines without a Docker endpoint;
both executed green in the frozen head full verify of this inventory.

`AlipayDependencyGraphContractTest` is tagged behavioral because it
resolves jars from the real test classpath via
`ProtectionDomain#getCodeSource`, which a pom text edit cannot forge.

## Anti-self-reference rule

A change-set may not author a `behavioral` test that asserts literal
strings the same change-set wrote into production source. Snapshot
style pins belong in the checksummed census tooling, and any genuine
snapshot test must be tagged `snapshot` and must pin pre-existing
state. `NativeCountTypingContractTest` predates this rule and is
grandfathered under the `snapshot` tag; do not add more like it.

## Reporting rule

Handoffs state the three counts separately, for example: 185 behavioral,
33 static contract, 1 snapshot, census tooling separate. Never present
the sum as coverage.
