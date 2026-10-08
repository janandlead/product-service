# Verification record

Recorded during implementation on 2026-10-08.

| Check | Result |
|---|---|
| `mvn test` | Passed |
| JWT/security references removed from application source | Passed |
| Correlation-ID filter and response context removed | Passed |
| Lombok compilation and Spring context startup | Passed through `mvn test` |
| OpenAPI configuration and controller tests | Passed through `mvn test` |
| PostgreSQL integration suite | Requires a running PostgreSQL instance and dedicated test database |

The standard test command runs service and MVC controller tests without PostgreSQL. The integration profile uses the PostgreSQL settings from `src/test/resources/application-integration.yml` and must target the disposable `product_test` database.

## Normal-environment verification

With JDK 21, Maven, and PostgreSQL available:

```shell
mvn clean verify
mvn -Pintegration verify
mvn spring-boot:run
```

Then check health, Swagger UI, the API examples, and the Postman collection. The application currently has no authentication layer; if a deployment adds gateway authentication, verify those gateway rules separately.

Temporary build and test artifacts are under ignored `target/` and are not part of the application source or Maven build configuration.
