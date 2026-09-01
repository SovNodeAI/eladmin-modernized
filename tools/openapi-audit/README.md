# openapi annotation audit

A dependency-free, single-file Java 21 tool that statically verifies the
OpenAPI 3 annotation migration across this checkout. It never starts the
application and never uses the network.

## Usage

From the repository root:

```
java tools/openapi-audit/OpenApiAudit.java .
```

Exit code 0 prints `OPENAPI AUDIT OK`; any drift exits nonzero with the
first failing check.

## What the tool enforces

- No `io.swagger.annotations` (Swagger 1.x) references remain in any Java
  source or FreeMarker template under `src/main`.
- The exact annotation census holds: 26 `@Tag`, 3 `@Hidden` in total
  (1 class level on `AppRun` plus 2 method level on the two hidden
  Alipay callback handlers in `AliPayController`), 141 `@Operation`,
  414 `@Schema`, and exactly 1 fully qualified OpenAPI `RequestBody`
  description in the generated delete handler.
- `pom.xml` pins `org.springdoc:springdoc-openapi-starter-webmvc-ui` to
  3.1.0 directly, the Boot 4 compatible line, and contains no
  `io.swagger:swagger-annotations` 1.x dependency.
- The Security 6 whitelist keeps permitting `/v3/api-docs/**`,
  `/swagger-ui/**`, `/swagger-ui.html`, `/doc.html`, and `/webjars/**`.
- `application.yml` keeps `path: /doc.html` so the documentation entry
  point stays at the same URL.

## Provenance

The expected counts were taken from the working tree that completed the
OpenAPI annotation migration and were cross-checked against the endpoint
census (139 endpoints across 26 `@RestController` classes). Update the
constants deliberately, together with the code change that shifts them.
