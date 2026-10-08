# Product Service

An independent Java 21 / Spring Boot 3 Maven application on port **8083**. It owns the product catalog, inventory counters, and per-order reservations in PostgreSQL `product_db`.

## Documentation

| Document | What it covers |
|---|---|
| [Detailed development notes](docs/DEVELOPMENT_NOTES.md) | Layer-by-layer code walkthrough, JPA, validation, transactions, locking, logging, tests, and learning exercises |
| [API walkthrough](docs/API.md) | Requests, responses, and expected database changes |
| [Inventory consistency](docs/CONCURRENCY.md) | Optimistic locking, retry semantics, and reservation state transitions |
| [Verification record](docs/VERIFICATION.md) | Checks completed and checks still requiring a normal development environment |
| [Postman collection](postman/product-service.postman_collection.json) | Importable requests with example responses and status assertions |
| [Production schema baseline](docs/schema-production.sql) | SQL to review and adopt through a migration tool |

Start with the setup instructions below to run the service. Read the development notes alongside the Java files to understand the implementation.

## Features

- Product creation, lookup, update, soft deletion, and case-insensitive search.
- Database pagination and sorting with an allowed field list.
- Inventory lookup and signed administrative stock adjustments.
- Atomic multi-item reservations and idempotent release/confirmation.
- Version checks to prevent competing orders from overselling stock.
- Request validation, consistent errors, and structured business-operation logs.
- Swagger documentation, basic Actuator health, service tests, MockMvc tests, and PostgreSQL integration tests.

**Verification status:** the full Maven test suite passes locally. PostgreSQL integration tests still require a running PostgreSQL instance as described in the [verification record](docs/VERIFICATION.md).

## Architecture

```mermaid
flowchart LR
  Client[Client / Gateway :8080] --> PC[ProductController]
  Order[Future Order Service :8084] --> IC[InventoryController]
  PC --> PS[ProductService]
  IC --> IS[InventoryService]
  PS --> Repositories[Spring Data JPA repositories]
  IS --> Repositories
  Repositories --> DB[(PostgreSQL product_db)]
```

Controllers validate DTOs and delegate. Services own transactions and business rules. Repositories execute database queries; entities protect stock invariants; mappers produce immutable response records. No entity is returned by an API. Order Service must call HTTP APIs and never access this database.

Product creation and initial inventory commit together. Multi-product reservation, release, and confirmation each use a single local transaction. PostgreSQL is required for the per-order transaction lock; this deliberately avoids an in-memory lock that would fail across multiple service instances.

## Project structure

```text
product-service/
├── pom.xml
├── README.md
├── docs/
│   ├── API.md
│   ├── DEVELOPMENT_NOTES.md
│   ├── CONCURRENCY.md
│   ├── VERIFICATION.md
│   └── schema-production.sql
├── postman/product-service.postman_collection.json
├── scripts/concurrent-reservation.ps1
└── src/
    ├── main/
    │   ├── resources/application.yml
    │   └── java/com/ecommerce/product/
    │       ├── ProductServiceApplication.java
    │       ├── enums/
    │       │   ├── ProductStatus.java
    │       │   └── ReservationStatus.java
    │       ├── entity/
    │       │   ├── Product.java
    │       │   ├── Inventory.java
    │       │   └── InventoryReservation.java
    │       ├── dto/
    │       │   ├── request/
    │       │   │   ├── ProductCreateRequest.java
    │       │   │   ├── ProductUpdateRequest.java
    │       │   │   ├── InventoryAdjustmentRequest.java
    │       │   │   ├── InventoryReservationRequest.java
    │       │   │   └── OrderInventoryRequest.java
    │       │   └── response/
    │       │       ├── ProductResponse.java
    │       │       ├── InventoryResponse.java
    │       │       ├── InventoryReservationResponse.java
    │       │       ├── PageResponse.java
    │       │       └── ErrorResponse.java
    │       ├── repository/
    │       │   ├── ProductRepository.java
    │       │   ├── InventoryRepository.java
    │       │   ├── InventoryReservationRepository.java
    │       │   └── OrderTransactionLock.java
    │       ├── mapper/ProductMapper.java
    │       ├── service/
    │       │   ├── ProductService.java
    │       │   ├── InventoryService.java
    │       │   └── impl/
    │       │       ├── ProductServiceImpl.java
    │       │       └── InventoryServiceImpl.java
    │       ├── controller/
    │       │   ├── ProductController.java
    │       │   └── InventoryController.java
    │       ├── exception/
    │       │   ├── ProductNotFoundException.java
    │       │   ├── DuplicateSkuException.java
    │       │   ├── InsufficientStockException.java
    │       │   ├── InvalidInventoryOperationException.java
    │       │   └── GlobalExceptionHandler.java
    │       └── config/
    │           └── OpenApiConfig.java
    └── test/
        ├── resources/application-integration.yml
        └── java/com/ecommerce/product/
            ├── ProductServiceTest.java
            ├── InventoryServiceTest.java
            ├── ProductControllerTest.java
            ├── InventoryControllerTest.java
            └── PostgresInventoryIT.java
```

## Maven and configuration

[pom.xml](pom.xml) targets Java 21 and pins Spring Boot 3.5.13 with springdoc 2.8.17. Springdoc's [compatibility matrix](https://springdoc.org/v2/) pairs Boot 3.5.x with springdoc 2.8.x.

[application.yml](src/main/resources/application.yml) sets the service name, port, PostgreSQL connection, SQL logging, and health/info exposure. SQL logging is off by default; set `SHOW_SQL=true` for lessons. Timestamps are UTC (entity timestamps use LocalDateTime representing UTC).

Actuator metrics and tracing are disabled. The project adds no metrics code, registry, exporter, or telemetry integration. See [Spring's Actuator documentation](https://docs.spring.io/spring-boot/reference/actuator/metrics.html).

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/product_db` | Application database connection |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | Empty | Database password |
| `DDL_AUTO` | `update` | Local schema management; use `validate` with production migrations |
| `SHOW_SQL` | `false` | Enable SQL output during lessons |
| `TEST_DB_URL` | `jdbc:postgresql://localhost:5432/product_test` | Disposable integration database |
| `TEST_DB_USERNAME` | `postgres` | Integration database user |
| `TEST_DB_PASSWORD` | Empty | Integration database password |

The `TEST_DB_*` variables apply to the integration test configuration. They do not replace application database settings.

## Suggested teaching sequence

1. Read enums and entities: catalog lifecycle, stock invariants, reservation lifecycle.
2. Read request/response records: nested validation, decimal precision, explicit page metadata.
3. Read repositories and mapper: database pagination/search, escaping literal search wildcards, uppercase canonical SKUs.
4. Read service interfaces, then ProductServiceImpl: creation transaction, update, idempotent soft deletion.
5. Read InventoryServiceImpl: signed adjustment, reserve, release, confirm.
6. Read controllers: HTTP status codes, validated parameters, stable allowed sorting.
7. Read exceptions and GlobalExceptionHandler: consistent safe error documents.
8. Read OpenApiConfig.
9. Run the tests, then follow the Postman collection.

Product updates accept only name, description, and price. Unknown fields (including SKU, ID, stock, or status) return 400. Blank names, nonpositive prices, more than two decimal places, invalid IDs, empty item lists, and duplicate product IDs are rejected. Catalog pages exclude DELETED products. INACTIVE is modeled for future administration; new reservations require ACTIVE. Deleted SKUs remain reserved permanently.

## Errors, OpenAPI, and health

All application endpoints are available without an authentication header. Deployments that require authentication should enforce it at an API gateway or add a dedicated security layer before exposing the service publicly.

Swagger UI: http://localhost:8083/swagger-ui/index.html  
OpenAPI JSON: http://localhost:8083/v3/api-docs  
Health: http://localhost:8083/actuator/health

Swagger UI documents DTO validation, examples, status codes, and error schemas. Only health/info Actuator endpoints are exposed.

Mutation logs include the relevant product, order, and quantity identifiers. Credentials and adjustment reason text are not logged.

## Local startup and testing

Install **JDK 21**, Maven 3.9+, and PostgreSQL locally. Confirm `java -version` and `mvn -version` both use Java 21.

Create databases with PostgreSQL tools (Windows may require their full paths):

```shell
createdb -U postgres product_db
createdb -U postgres product_test
```

Or execute `CREATE DATABASE product_db;` and `CREATE DATABASE product_test;` in psql/pgAdmin, outside a transaction.

Configure environment variables in PowerShell:

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = Read-Host "PostgreSQL password"
mvn clean verify
mvn spring-boot:run
```

On Bash, use `export DB_USERNAME=postgres` and `read -s DB_PASSWORD; export DB_PASSWORD`. Do not commit passwords.

To run the packaged application:

```shell
java -jar target/product-service-1.0.0.jar
curl http://localhost:8083/actuator/health
```

Health returns `{"status":"UP"}` when the database is reachable. Import [the Postman collection](postman/product-service.postman_collection.json) and run the requests in order. It captures product/order IDs and asserts expected statuses. Saved responses illustrate the business scenarios.

Unit/service and MockMvc tests require no PostgreSQL:

```shell
mvn test
```

Integration tests use **real PostgreSQL**, never H2 or Docker:

```powershell
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/product_test"
$env:TEST_DB_USERNAME = "postgres"
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
mvn -Pintegration verify
```

The integration profile uses `create-drop` and clears its tables between tests. Point it only at the dedicated disposable `product_test` database. It does not silently skip tests when PostgreSQL is unavailable. Failsafe runs `*IT`; Surefire runs unit and controller `*Test` classes. The concurrency test uses separate transactions and a barrier forcing both contenders to read the same version.

Inspect data:

```sql
SELECT id, sku, name, price, status FROM products ORDER BY id;
SELECT product_id, total_quantity, reserved_quantity,
       total_quantity - reserved_quantity AS available, version
FROM inventory ORDER BY product_id;
SELECT order_id, product_id, quantity, status, version
FROM inventory_reservations ORDER BY order_id, product_id;
```

See [API examples and inventory changes](docs/API.md), [concurrency explanation](docs/CONCURRENCY.md), and [verification results](docs/VERIFICATION.md).

## Troubleshooting

| Symptom | What to check |
|---|---|
| Maven uses the wrong Java version | Compare `java -version` with `mvn -version`; set `JAVA_HOME` and PATH to JDK 21 |
| Dependencies cannot be downloaded | Check Maven Central connectivity, proxy settings, and local repository write access |
| PostgreSQL connection refused | Confirm PostgreSQL is running and `DB_URL` has the correct port and database |
| Database authentication failed | Check `DB_USERNAME`, `DB_PASSWORD`, and PostgreSQL authentication configuration |
| Port 8083 is occupied | Stop the conflicting local process or set `SERVER_PORT` and update client URLs |
| API request is rejected | Check the request path, HTTP method, JSON body, and validation constraints |
| Reservation returns 409 | Read the error code: insufficient stock, changed order payload, invalid state, and version conflicts require different responses |
| Integration tests remove test data | They intentionally use `create-drop`; configure a dedicated disposable database |
| An adjusted quantity increases twice | Adjustment applies a delta and is not retry-safe; inspect current stock before taking corrective action |

For a reproducible last-unit race, create an ACTIVE product with one available unit and run [the concurrency script](scripts/concurrent-reservation.ps1) with PowerShell 7.

## Production schema management

`ddl-auto: update` is a local teaching convenience, not a migration strategy. For production, use a dedicated database role and set `DDL_AUTO=validate`. Add Flyway (`flyway-core` and `flyway-database-postgresql`) or Liquibase, and move the reviewed [schema baseline](docs/schema-production.sql) into `db/migration/V1__initial_schema.sql` or a Liquibase changelog. Apply migrations before Hibernate validation. The SQL is documentation and is not run automatically.

On an existing database, inspect and reconcile its schema before baselining; do not run a fresh CREATE TABLE baseline over existing tables. Subsequent changes belong in new, reviewed versioned migrations. The baseline includes foreign keys, uniqueness, positive prices/quantities, and counter checks. Retain reservation history for idempotency; do not purge or reuse an order ID while clients can retry it.

No gateway, Order Service, Feign client, Resilience4j client, Eureka, Docker, Kubernetes, Config Server, or telemetry exporter is included. Lombok is used for boilerplate reduction in entities, constructors, and logging. Future Order Service clients should set HTTP timeouts and retry only appropriate failures with the same order ID and identical payload. A reservation response may report a terminal status on replay; clients must inspect it.
