# Test taxonomy

Each test class has one class-level JUnit tag. The categories are intentionally reported separately because a source-text contract is not behavioral coverage.

## Categories

- `behavioral` executes production code and asserts an observable result such as authorization, serialization, mapper output, mail delivery, or context startup.
- `static-contract` checks source, configuration, dependency, or seed-data invariants. These checks protect migration decisions but do not prove runtime behavior.
- `snapshot` pins a pre-existing migration shape. New snapshot tests should be avoided unless the reference state predates the change under test.

## Current inventory

| Module | Behavioral | Static contract | Snapshot |
| --- | ---: | ---: | ---: |
| `eladmin-common` | 29 | 0 | 0 |
| `eladmin-logging` | 4 | 0 | 0 |
| `eladmin-generator` | 24 | 12 | 1 |
| `eladmin-tools` | 70 | 0 | 0 |
| `eladmin-system` | 58 | 21 | 0 |
| **Total methods** | **185** | **33** | **1** |

The methods are distributed across 36 behavioral classes, 6 static contract classes, and 1 snapshot class. The complete workflow expects 43 Surefire reports and 219 executed methods with zero failures, errors, or skips.

## Reproduction

```bash
mvn -B -ntp clean verify
find . -path '*/target/surefire-reports/TEST-*.xml' -type f -print
```

The public workflow parses every Surefire XML report and fails if the report count, method count, or any failure, error, or skip differs from the expected inventory.

## Anti-self-reference rule

A behavioral test must not merely assert a literal introduced by the same change. Exact source strings belong in `static-contract` checks. A runtime claim needs an executable path through production code, and a test should fail for the claimed reason when the relevant control is removed.

## Reporting rule

Use: “185 behavioral tests, 33 static contract tests, and 1 migration snapshot.” Do not use the aggregate as a coverage percentage or imply that every production behavior is tested.
