# Public verification evidence

Everything in this directory is reproducible from a clean clone. No private filesystem, internal ticket system, or unpublished commit is required.

- [`CLAIMS_LEDGER.md`](CLAIMS_LEDGER.md) maps each release claim to tracked source or an executable check.
- [`TEST_TAXONOMY.md`](TEST_TAXONOMY.md) separates behavioral tests from static migration contracts and snapshots.
- [`RISK_MAP.md`](RISK_MAP.md) records covered and residual migration risks.
- [`REFUTER_EVIDENCE.md`](REFUTER_EVIDENCE.md) explains how the most important guards demonstrate that they can fail.

The canonical release gate is [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml). It runs the Docker-backed Maven suite, rejects skipped tests, verifies the endpoint census and OpenAPI inventory, and scans the repository for secrets.

Run the principal checks locally with Java 21, Maven 3.9 or newer, and Docker:

```bash
mvn -B -ntp clean verify
java tools/endpoint-census/EndpointCensus.java . tools/endpoint-census/endpoint-census.txt verify
java tools/openapi-audit/OpenApiAudit.java .
```

The expected results are 185 behavioral tests, 33 static contract tests, 1 migration snapshot, zero failures, zero errors, zero skips, 139 endpoint census records whose six semantic signature columns match the pinned upstream artifact, 141 OpenAPI operations, and 414 schemas.
