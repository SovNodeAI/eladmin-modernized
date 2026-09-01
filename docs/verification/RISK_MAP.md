# Risk map

Ranked by blast radius times silence: what breaks silently if the
migration is wrong, and which executable evidence now guards it. This
is the budget rule for test writing: risk orders the spending, not
cheapness of the check.

| Rank | Subsystem | Silent failure mode | Guard added in this delta | Status |
|---|---|---|---|---|
| 1 | Alipay dependency graph | SDK transitively restores vulnerable fastjson 1.x and exposes remote-code-execution surface | SDK exclusion plus Maven Enforcer ban; classpath-absence regression and local RSA page-signing control | closed for the application paths in use |
| 2 | Email send path | `NoClassDefFoundError` on first send, escapes `catch (Exception)`, surfaces as 500 on both verification code flows and the Quartz failure alarm | `EmailServiceImplBehaviorTest` through a real SMTPS server; red on the old code | closed |
| 3 | HTTP JSON converter | Framework 7 removes the deprecated registration hook; fastjson behaviors (date format, long precision, null omission, reference detection) silently flip to Jackson defaults and reshape the API | `FastJsonConverterShapeCharacterizationTest` pins the exact bytes; README documents the constraint | closed, monitor on Framework upgrades |
| 4 | MapStruct codegen | Processor cannot see Lombok accessors, emits compilable empty mappers; all DTOs null at runtime | Processor line pinned via `annotationProcessorPaths`; field asserting round trips in three modules | closed |
| 5 | Security chain registration | Anonymous route registration regression 401s the Alipay payment callbacks; ERROR dispatch flips error pages to 401 JSON | Parity stubs for both callbacks and the ALL bucket; ERROR dispatch assertion with red/green refuter evidence | closed |
| 6 | Context boot | Autoconfiguration wiring (springdoc, Druid Boot 4 starter, Redis namespace) fails only at startup | `EladminSystemApplicationTests` with MySQL and Redis containers, executed green in the final gate | closed where Docker exists; skips honestly elsewhere |
| 7 | Redis value wire format | Serializer change breaks reads of existing cached data | `@type` prefix pin plus round trip, unit and live | closed |
| 8 | Endpoint inventory | Route normalization drift hides lost or invented endpoints | Checksummed census with the Spring `combine` join fix; verify mode detects tampering (observed failing against the stale baseline) | closed |
| 9 | Generator configuration | Unsupported Commons Configuration 1.x remains in the runtime graph for one packaged properties file | JDK `Properties` loader, known and unknown type controls, classpath-absence regression, and Maven Enforcer ban | closed |
| 10 | Quartz job execution | Job failures during scheduling changes never surface in unit gates | none in this delta | open, accepted |
| 11 | Alipay live signing | BC 1.62 to 1.85.2 jump verified only by jar identity and local SDK signing | `AlipaySdkCompatibilitySmokeTest` (local), README risk register asks deployments to smoke test real signing | mitigated, residual accepted |
| 12 | Generator template output | Template rendering diverges from expected annotations | `GeneratorTemplateContractTest` pins template text (static) | scaffolding only |

Budget lesson recorded from the review: the migration wrote roughly
1,400 test lines while the five riskiest subsystems (mail, mappers,
Redis, converter, boot) had zero tests, because grep checks are cheap
tokens. This delta spent its budget on rows 1 through 6.
