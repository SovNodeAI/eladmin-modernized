# Compatibility and behavior changes

This fork preserves the tracked controller inventory while deliberately changing several security and onboarding behaviors. Integrators should account for these differences instead of assuming byte-for-byte runtime behavior outside the endpoint census.

## Runtime and framework

- Java 8 is replaced by Java 21.
- Spring Boot 2.7.18 is replaced by Spring Boot 4.1.1 and Spring Framework 7.
- Java EE imports are migrated to Jakarta EE.
- Springfox is replaced by springdoc OpenAPI.
- The project version is `1.0.0-sov`; it is not an official eladmin version.
- A Spring profile must be selected explicitly. The packaged application no longer defaults silently to development settings.

## Credentials and onboarding

- Repository credentials, password hashes, and machine-local paths were replaced with environment placeholders or onboarding markers.
- `tools/onboard-local.sh` creates ignored local credentials and database bootstrap material.
- The Druid console and S3 integration are disabled by default.
- Required production database, Redis, JWT, RSA, and DES inputs fail closed when absent or invalid.

## Authorization changes

- Local storage, S3, Alipay, email, generator, generator configuration, and verification-code routes now have explicit method-security boundaries.
- Generator seed data now grants the five permissions required by its controllers: `generator:list`, `generator:edit`, `generator:code`, `genConfig:list`, and `genConfig:edit`.
- Verification-code send and validation routes require `email:send`. A normal user following the unchanged upstream email-change UI will receive HTTP 403 unless the role is deliberately granted that permission or the frontend is adapted to a separate self-service flow.
- Password reset requires `user:edit`; low-privilege authenticated users are denied.

## Password workflow changes

- User creation and administrative password reset no longer use the shared `123456` default.
- Each operation generates a random twelve-character one-time password and returns it once as `{"password":"..."}`.
- The stock `eladmin-web` frontend does not display this new response today. Integrators must add a secure one-time display or delivery step before relying on these operations.
- Password hashes are excluded from login, `/auth/info`, and cache serialization responses.

## Frontend compatibility

The tracked 139 backend endpoint declarations are preserved, but the authorization and response changes above require frontend work. Use the upstream Vue frontend as a starting point, not as proof that the changed flows are complete. At minimum, update user creation, password reset, and email-change handling and verify role-menu assignments against the seed database.

## Known residual risks

- DES/CBC remains for compatibility with legacy encrypted values. The hard-coded key is gone, but the cipher is still obsolete.
- Remote deployment and database connection testing are privileged, high-impact features and should be isolated or disabled when unnecessary.
- See [`docs/security/UPSTREAM_CVE_STATUS.md`](docs/security/UPSTREAM_CVE_STATUS.md) and [`docs/verification/RISK_MAP.md`](docs/verification/RISK_MAP.md) for the current review boundary.

## Development disclosure

The modernization was implemented with AI-assisted engineering under human direction. Public claims are limited to reproducible repository evidence and independently reviewed build results. AI assistance is not presented as a substitute for deployment-specific testing, security review, or operator judgment.
