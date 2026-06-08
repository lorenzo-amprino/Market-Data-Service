# Market Data Service

Market Data Service provides canonical market reference data and historical market information for the platform.

## Stack

- Java 25
- Spring Boot
- Maven
- PostgreSQL
- Flyway
- JPA plus Spring JDBC for explicit SQL paths
- JUnit 5, AssertJ, Spring Boot Test, and Testcontainers PostgreSQL

## Project structure

```text
src/main/java/com/lamprino/marketdata
  api/            HTTP API boundaries
  application/    use-case orchestration
  domain/         domain model, repositories, services, policies
  persistence/    JPA, JDBC, and migration-related adapters
  provider/       Data Provider adapters
  config/         Spring configuration
```

This scaffold intentionally contains no Market Data domain/API behavior beyond application bootstrapping.

## Requirements

- JDK 25
- Maven 3.9+ (or the included Maven Wrapper)
- Docker, for local PostgreSQL and Testcontainers-based integration tests

## Local PostgreSQL

Start PostgreSQL:

```sh
docker compose up -d postgres
```

Stop it:

```sh
docker compose down
```

Remove the local database volume:

```sh
docker compose down -v
```

Default local database settings:

| Setting | Value |
| --- | --- |
| Database | `market_data` |
| Username | `market_data` |
| Password | `market_data` |
| JDBC URL | `jdbc:postgresql://localhost:5432/market_data` |

## Build and test

Build from a clean checkout:

```sh
./mvnw clean verify
```

Run integration tests with Testcontainers PostgreSQL:

```sh
./mvnw verify -Pintegration-tests
```

Run the application against the local PostgreSQL instance:

```sh
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Health and PostgreSQL-backed readiness checks are available at:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

The `local` profile reads these optional environment variables:

- `MARKET_DATA_DB_URL`
- `MARKET_DATA_DB_USERNAME`
- `MARKET_DATA_DB_PASSWORD`

## Docker image

Build the service container:

```sh
docker build -t market-data-service .
```

Run the service container on the host network against local PostgreSQL:

```sh
docker run --rm --network host \
  -e SPRING_PROFILES_ACTIVE=local \
  market-data-service
```

## Flyway migrations

Flyway is configured to load migrations from:

```text
src/main/resources/db/migration
```

The initial v1 schema is intentionally not implemented in this scaffold.
