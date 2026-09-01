# Adversarial refuter evidence

Each load bearing claim in this delta was attacked: the fix was
removed or the assertion inverted, and the suite was required to go
red for the claimed reason. A guard that stays green when its fix is
removed is theater and gets fixed or dropped.

| # | Claim under attack | Probe | Result | Evidence |
|---|---|---|---|---|
| 1 | The mail test catches the javax linkage break | Reverted `EmailServiceImpl` to `cn.hutool.extra.mail.Mail` and reran only `EmailServiceImplBehaviorTest` | Red with `java.lang.NoClassDefFoundError: javax/mail/Multipart` at `EmailServiceImpl.send(EmailServiceImpl.java:95)` | `03-mail-test-OLD-CODE.log` |
| 2 | The ERROR dispatch guard catches removal of the dispatcher matchers | Commented out `authz.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` and reran only `errorDispatchStaysPermittedSoErrorPagesSurvive` | First probe stayed green: the test targeted an already permitted path, so it could not discriminate. Test rebuilt to fire the ERROR dispatch at `/error` as the container does. Second probe: red with the production symptom `{"status":401,...,"message":"登录状态已过期，请重新登录"}` | `14-refuter-error-dispatch-RED.log` (probe 2), fixed test green in `15-refuter-error-GREEN.log` |
| 3 | The census tool detects baseline drift | Ran verify mode against the old baseline after the join fix, before regenerating | Red: `CENSUS FAILED: artifact drift at byte offset 11812 (line 66)`; after regeneration `GENERATE OK: 139 endpoint records` and verify returns green with the new checksum | terminal transcript captured during the regeneration step of this delta |
| 4 | The mapper tests catch empty generated mappers | Field level assertions on every mapped field including nested collections; an empty mapper yields null fields and fails the first assertion | Structural: no probe needed, assertions read every field, `UserMapperImpl` is instantiated directly | `05-system-tests.log`, green runs per module |
| 5 | The converter shape pins catch feature loss | Assertions are exact JSON strings; dropping `ReferenceDetection` or the date format changes bytes and fails | Structural: exact string equality | `07-converter-test.log` |
| 6 | The no-excludes audit catches hidden tests | Assertion added after removing the system pom exclusion; re-adding any `<exclude>` in any module fails `noModuleHidesTestsBehindSurefireExclusions` | Structural | `13-full-gate-p2.log` |
| 7 | The banner guard catches a stale hard coded Boot version | Reverted `banner.txt` to the upstream literal `(v2.6.4)` and reran only `StartupBannerVersionTest` | Red with `banner must print the resolved Boot version 4.1.1` and the stale banner in the captured console | `18-banner-refuter-RED.log` |
| 8 | The linkage sweep count is reproducible from a clean checkout | Detached worktree at 0cdc14d, `mvn clean install -DskipTests`, then the documented `dependency:build-classpath` command and sweep | 259 jars scanned, same single offender (hutool-all 5.8.35, 4 class files), matching the independent verifier; the README's earlier 182 came from a stale classpath snapshot and was corrected | `19-linkage-sweep-recheck.txt` |

## Honest findings about our own guards

- Probe 2 initially exposed a weak test (an ERROR dispatch probe aimed
  at a permitted path can never fail). The probe result was treated as
  a defect of the guard, and the guard was rebuilt before being
  trusted. This is exactly the refuter loop working as intended.
- The boot slice depends on a Docker endpoint. In an environment with
  none it reports skipped, not passed; no claim of context boot is made
  from a skipped run. In the final gate of this delta it executed.
- The default gate caught a real accident mid delta: the banner fix
  was briefly reverted by a `git checkout` while uncommitted, and the
  full suite went red on `StartupBannerVersionTest` until the edit was
  reapplied (`20-final-gate-v3.log` shows the green rerun). The guard
  works against careless reverts, not just deliberate probes.
