# Verification record

Recorded during implementation on 2026-10-08.

| Check | Result |
|---|---|
| Java source compilation, including all tests, with `javac --release 21 -parameters` | Passed using cached libraries |
| ProductServiceTest and InventoryServiceTest | 25 tests passed using a temporary JUnit launcher and cached libraries |
| Postman collection JSON | Parsed successfully; 16 sequential requests |
| Source inspection | No TODO implementations, Lombok, or System.out.println |
| Declared Maven dependency resolution/build | Blocked by environment networking |
| MockMvc controller suite against cached libraries | Inconclusive: incompatible cached Spring Security versions; 7 passed, 17 failed during dependency linkage |
| PostgreSQL integration suite | Not executed: PostgreSQL server launcher blocked by sandbox restrictions |

The environment has JDK **26.0.2.1**, not Java 21. `--release 21` checks Java 21 language/API compatibility, but does not replace running the application on JDK 21.

The available Maven cache is incomplete for the declared Spring Boot 3.5.13 / springdoc 2.8.17 dependency set. Maven first could not write its configured cache; a workspace-local cache avoided that issue, but Maven Central access then failed with `Permission denied: getsockopt`. No successful Maven package is claimed.

For a useful partial check, sources were compiled against available cached jars (including Boot 3.4.10, Spring Framework 6.2.11, and a mixed Security 6/7 cache). The 25 service tests passed under that fallback. The controller fallback encountered `NoClassDefFoundError` for Security classes introduced in a different major version. These fallback results do not establish compatibility of the exact declared dependency set.

A disposable PostgreSQL cluster was initialized under ignored `target/`, but `pg_ctl` could not launch the server under the restricted sandbox token. The real database concurrency tests are present and not replaced with H2 or mocks.

## Required normal-environment verification

With JDK 21, Maven Central access, PostgreSQL, and the environment variables described in README:

```shell
mvn clean verify
mvn -Pintegration verify
mvn spring-boot:run
```

The project contains 60 test methods: 25 service tests, 24 MockMvc tests, and 11 PostgreSQL integration tests. Run the full suite before deployment. Then verify health, Swagger, and the supplied Postman collection using Auth Service-issued tokens.

Temporary compilation/test artifacts and the disposable database files are under ignored `target/`. They are not part of the application source or Maven build configuration.

