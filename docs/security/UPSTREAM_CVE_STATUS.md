# Upstream eladmin CVE status

This is a source review of public NVD entries whose identifiers begin with `CVE-2025` and name eladmin. It is not a penetration-test report or a claim that the application is free of vulnerabilities.

As of August 31, 2026, the NVD keyword feed returns ten such entries published during 2025, plus `CVE-2025-70997`, which was published in 2026. Earlier summaries that counted seven entries predated the later records.

Status meanings:

- **Closed here**: this fork contains a specific control and a tracked regression check for the described path.
- **Inherited mitigation**: the upstream baseline already changed the path, or access is narrowed, but this fork does not claim a full exploit replay.
- **Residual risk**: the relevant primitive remains and deployments must mitigate or disable it.

| CVE | NVD description in brief | Status in this fork | Evidence and limitation |
| --- | --- | --- | --- |
| [CVE-2025-22978](https://nvd.nist.gov/vuln/detail/CVE-2025-22978) | Formula injection in exception-log export | **Closed here** | The upstream export fix is included in the stated baseline, and `FileUtil.downloadExcel` centrally sanitizes formula-leading strings. `FileUtilTest` covers the sanitizer. |
| [CVE-2025-2855](https://nvd.nist.gov/vuln/detail/CVE-2025-2855) | Command execution through deployment `checkFile` data | **Inherited mitigation** | The baseline replaced the original direct command construction and the operation requires `deploy:edit`. Remote deployment remains shell-oriented and has not received a fork-specific exploit replay, so it should be isolated or disabled. |
| [CVE-2025-3250](https://nvd.nist.gov/vuln/detail/CVE-2025-3250) | Unsafe database connection testing | **Residual risk** | `/api/database/testConnect` requires the explicit `database:testConnect` permission, but a privileged caller can still direct an outbound database connection. Apply network egress restrictions. |
| [CVE-2025-8530](https://nvd.nist.gov/vuln/detail/CVE-2025-8530) | Default Druid console credentials | **Closed here** | The console is disabled by default, limited to loopback by default when enabled, and takes its username and password from environment variables. Startup guards and configuration contract tests reject unsafe production settings. |
| [CVE-2025-9239](https://nvd.nist.gov/vuln/detail/CVE-2025-9239) | Hard-coded DES key and weak encryption | **Residual risk with partial mitigation** | The public hard-coded key and IV were removed and an external eight-character key is required. DES/CBC itself remains for stored-value compatibility and must be replaced before treating this path as modern cryptography. |
| [CVE-2025-9240](https://nvd.nist.gov/vuln/detail/CVE-2025-9240) | Password-hash disclosure through `/auth/info` | **Closed here** | `UserDto.password` and `JwtUserDto.getPassword()` are excluded from fastjson2 serialization. `UserPasswordNonSerializationTest` covers direct DTO, cache round-trip, login response, and wrong-password behavior. |
| [CVE-2025-9241](https://nvd.nist.gov/vuln/detail/CVE-2025-9241) | Formula injection in user export | **Closed here** | All exports passing through `FileUtil.downloadExcel` sanitize leading formula characters. The sanitizer has behavioral tests. |
| [CVE-2025-9937](https://nvd.nist.gov/vuln/detail/CVE-2025-9937) | Missing authorization on local-storage deletion | **Closed here** | Deletion requires `storage:del`; upload, edit, list, and delete paths have positive and low-privilege negative controls. |
| [CVE-2025-10014](https://nvd.nist.gov/vuln/detail/CVE-2025-10014) | Improper authorization in email update | **Inherited mitigation** | The update path derives the account from the authenticated principal instead of trusting a request ID, verifies the current password, and consumes a code for the requested address. Verification-code issuance is additionally gated by `email:send`, which changes stock frontend behavior. |
| [CVE-2025-10084](https://nvd.nist.gov/vuln/detail/CVE-2025-10084) | Improper authorization on error-log detail | **Requires administrator authority** | The route uses `@el.check()` with no named permission. In `AuthorityConfig`, that form succeeds only for the `admin` authority. This is inherited access behavior, not a fork-specific exploit replay. |
| [CVE-2025-70997](https://nvd.nist.gov/vuln/detail/CVE-2025-70997) | Arbitrary password reset by any authenticated user | **Closed for low-privilege access** | Reset now requires `user:edit`, generates a random one-time password, and is covered by method-security and low-privilege denial tests. A role deliberately granted `user:edit` remains trusted to manage passwords. |

## Additional public records

NVD also lists eladmin records under 2024 and 2026 identifiers. This table is scoped to `CVE-2025-*` because that was the release-review request. Before deployment, review the live [NVD eladmin search](https://nvd.nist.gov/vuln/search/results?query=eladmin&search_type=all) and this repository’s [`SECURITY.md`](../../SECURITY.md).

## Deployment guidance

Do not enable remote deployment, database connection testing, the Druid console, payment, mail, or object storage merely because the application starts. Grant each permission only to a dedicated administrative role, constrain outbound network access, and replace DES-protected stored values as part of production migration.
