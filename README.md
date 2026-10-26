# spring-reactive-r2dbc-template

A **general-purpose backend service template**. Clone it, rename a few things, delete what you don't need — and you already have a running, authenticated, observable, database-backed microservice instead of a blank repo.

The goal is to make the boring setup decisions once, correctly, so that starting a new service (or a prototype to validate an idea) takes minutes, not weeks:

- opinionated **hexagonal package layout** that scales from throwaway prototype to production service
- **Keycloak** authentication pre-wired (resource server + client credentials) with a local Keycloak in Docker Compose
- **PostgreSQL + R2DBC + Liquibase** persistence with a full example vertical slice (CRUD, paging, filtering, auditing)
- **Kafka**, **gRPC**, **WebClient + circuit breaker**, **OpenTelemetry**, **Swagger**, **Testcontainers**, **CI** — all configured and demonstrated by example
- every optional feature lives in its own package/dependency, so **unwanted features can be removed without untangling the rest** (see [Removing optional features](#removing-optional-features))

## Tech stack

| Concern            | Technology                                                        |
|--------------------|-------------------------------------------------------------------|
| Language           | Kotlin 2.4.20 (JVM, coroutines preferred over Project Reactor)    |
| Framework          | Spring Boot 4.1.1 (WebFlux reactive stack)                        |
| Persistence        | Spring Data R2DBC + PostgreSQL (`r2dbc-postgresql`)               |
| Migrations         | Liquibase (YAML changelogs, run on startup)                       |
| Messaging          | Kafka (Spring Kafka, JSON serialization)                          |
| RPC                | gRPC via `spring-grpc` + `grpc-kotlin` (protobuf codegen)         |
| Security           | Spring Security OAuth2 resource server & client (Keycloak)        |
| Resilience         | Resilience4j circuit breaker (reactor) + `WebClient`              |
| Observability      | Actuator, Micrometer/Prometheus, OpenTelemetry (traces/metrics/logs) |
| API docs           | springdoc-openapi (Swagger UI)                                    |
| Testing            | JUnit 5, MockK, springmockk, WebTestClient, Testcontainers, WireMock |
| Build              | Gradle 9 (Groovy DSL), Java 24 toolchain                          |
| Local infra        | Docker Compose (PostgreSQL ×2, Kafka KRaft, Keycloak)             |
| CI                 | GitHub Actions: build + test on every branch/PR; pushes to `main`/`release/*` also publish a multi-arch Docker image to Docker Hub |

## Quick start

### 1. Start the infrastructure

```bash
./init_volumes.sh        # creates bind-mount dirs required by docker-compose.yml
docker compose up -d
```

| Service               | Host port | Notes                                              |
|-----------------------|-----------|----------------------------------------------------|
| PostgreSQL (app)      | `5432`    | db `application`, user/password `admin`            |
| PostgreSQL (Keycloak) | `5433`    | db `keycloak`                                      |
| Kafka (KRaft)         | `9094`    | topics auto-created: `sample_topic_2`, `keycloak-events`, `keycloak-admin-events` |
| Keycloak              | `8090`    | admin/admin, realm `app`                           |

### 2. Run the application

```bash
./gradlew bootRun
```

- HTTP: <http://localhost:8082>
- gRPC: `localhost:9082` (server reflection enabled)
- Swagger UI: <http://localhost:8082/swagger-ui.html>
- Actuator: <http://localhost:8082/actuator> (all endpoints exposed, health details on)

Liquibase migrations run automatically on startup. If no OTLP collector is running, use the `local` profile to disable telemetry export:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3. Authenticate via Keycloak

Endpoints are protected as an OAuth2 resource server against the `app` realm:

- `/permissions/**`, `/external/**` — require `ROLE_ADMIN`
- `/sample-secured/**` — require an authenticated principal
- `/actuator/**`, swagger paths, `/sample/**`, `/async/**` — public (`/rsocket/**` is also permitted but unused, a leftover from an earlier experiment)

The [`http/`](http) directory contains IntelliJ HTTP Client scripts (`auth.http`, `user-app.http`) that obtain tokens via password and client-credentials grants and exercise the API end to end. Copy `http-client.env.template.json` to `http-client.env.json` and fill in the values.

### 4. Reshape it into *your* service

1. Rename the project (`settings.gradle`, `spring.application.name`, group/artifact in `build.gradle`) and the base package `com.softeno.template`.
2. Replace the `permission` slice with your first entity: copy the package layout (api/service/db/mapper), add your Liquibase changelog, and the controller → service → repository → table wiring already works.
3. Delete the sample packages and dependencies you don't need (see below).

## Project structure

```
├── build.gradle                     # all versions pinned in one ext block; features are dependency-isolated
├── docker-compose.yml               # Postgres ×2, Kafka (KRaft), Keycloak w/ Kafka event provider
├── Dockerfile                       # temurin-24-jre + bootJar, `docker` profile
├── init_volumes.sh                  # creates bind-mount dirs used by compose
├── http/                            # IntelliJ HTTP Client scripts (auth + API calls)
├── tunnels/                         # optional SSH tunnel config for remote environments
├── .github/workflows/               # CI: build + test (JDK 24 / Gradle 9.3.1)
└── src/
    ├── main/kotlin/com/softeno/template/
    │   ├── SoftenoMvcJpaApp.kt          # entry point (component scan, R2DBC auditing, Kafka)
    │   ├── app/                         # ← application domain (keep this, replace the slice)
    │   │   ├── permission/              #   example vertical slice: CRUD + paged/filtered search
    │   │   │   ├── api/                 #     controller (adapter)
    │   │   │   ├── service/             #     business logic
    │   │   │   ├── db/                  #     R2DBC repository (adapter)
    │   │   │   └── mapper/              #     entity ↔ DTO mapping
    │   │   ├── kafka/                   #   Kafka producer/listener + Keycloak event listener
    │   │   ├── common/                  #   shared helpers (PrincipalHandler)
    │   │   └── config/                  #   security (Keycloak JWT) & OpenTelemetry config
    │   └── sample/                      # ← reference integrations only — safe to delete
    │       ├── http/                    #   functional router, WebClient + circuit breaker demo
    │       └── proto/                   #   gRPC service & client sample (all 4 call patterns)
    ├── main/proto/schema.proto          # gRPC contract (unary, server/client/bidi streaming)
    ├── main/resources/
    │   ├── application*.properties      # default / local (no OTLP) / docker profiles
    │   └── db/                          # Liquibase changelogs
    └── test/kotlin/com/softeno/template/    # Testcontainers-based integration tests
```

Hexagonal rules: business logic lives in `service`, DTO conversion in `mapper`, and framework touchpoints are confined to the adapters (`api` controllers, `db` repositories, `kafka` listeners) — Spring bean annotations and R2DBC mapping annotations do appear on services and entities, but domain rules stay free of web/messaging plumbing. Public endpoints use `suspend` functions and `Flow`; Reactor is bridged in only where an API requires it. Entities extend `BaseEntity` (UUID + `@Id`), and audit columns (`created_by/date`, `modified_by/date`) are filled automatically from the JWT subject via `ReactiveAuditorAware`; `@Version` provides optimistic locking.

## Features

| Feature | What you get | Where |
|---------|--------------|-------|
| Reactive HTTP API | WebFlux endpoints as coroutines (`suspend` / `Flow`), functional routers, request validation | `app/permission/api`, `sample/http/api` |
| Keycloak auth | JWT resource server, realm-role → authority mapping, CORS, client-credentials calls out | `app/config/security/SecurityConfig.kt`, compose `keycloak` |
| Authenticated auditing | `created_by` / `modified_by` populated from the token subject | `ReactiveAuditorConfig.kt` |
| Database + migrations | R2DBC PostgreSQL, sequences, unique constraints, migrations on startup | `spring.r2dbc.*`, `resources/db/` |
| Example vertical slice | Full CRUD with paging, sorting, text/date/author filters, count endpoint | `app/permission/` |
| Kafka | JSON producer + coroutine `@KafkaListener`, auto-created topics; the example slice emits a `CREATED_PERMISSION` event on create | `app/kafka/` |
| Keycloak → Kafka events | Login/logout/register and admin events streamed from Keycloak into Kafka and consumed as typed DTOs | compose `xcodeassociated/keycloak-kafka` image, `KafkaKeycloakController.kt` |
| gRPC | protobuf codegen, coroutine service with unary + all streaming patterns, client stub injection, reflection | `sample/proto/`, `src/main/proto/` |
| Resilient HTTP calls | `WebClient` with timeouts + Resilience4j circuit breaker and fallback | `sample/http/config/` |
| Observability | Actuator, Prometheus metrics, OTLP traces/logs/metrics, trace IDs in logs and coroutine context propagation | `app/config/opentelemetry/`, `logback-spring.xml`, actuator props |
| API docs | springdoc/Swagger UI | `springdoc.*` props |
| Testing | Testcontainers (Postgres, Kafka), WebTestClient, WireMock, MockK/springmockk, fixtures/builders | `src/test/` |
| Packaging & CI | Dockerfile + compose, GitHub Actions build/test, multi-arch image publish on `main` | `Dockerfile`, `.github/workflows/` |

Everything except the reactive web stack, build tooling, and layout is **optional by design** — each feature is a self-contained set of dependencies, one package, and a few config keys.

## Removing optional features

Cross-references are deliberately minimal — the only ones that exist are flagged as **Cross-references** in the table below. After removals run `./gradlew build` to confirm the service still compiles and starts.

| Remove | Steps |
|--------|-------|
| **gRPC** | Delete `sample/proto/`, `src/main/proto/`, the `protobuf` plugin + `grpc-*`/`spring-grpc` deps and `grpckt` codegen in `build.gradle`, `spring.grpc.*` props, the grpc method + stub in `sample/http/api/ExternalController.kt`, `http/grpc.sh`, and grpc bits in `IntegrationTest.kt` |
| **Kafka** | Delete `app/kafka/`, `@EnableKafka` in `SoftenoMvcJpaApp.kt`, `com.softeno.kafka.*` + `spring.kafka.*` props, the kafka starter dependency, and the `kafka-broker`/`init-kafka` compose services (drop the Keycloak event provider env vars too). **Cross-references:** remove the `KafkaSampleProducer` injection + send call in `app/permission/service/PermissionService.kt` and the `KafkaContainer` setup from `IntegrationTest.kt` |
| **Keycloak events** | Delete `app/kafka/KafkaKeycloakController.kt` + `com.softeno.kafka.keycloak` prop; swap the compose `keycloak` image for the stock `quay.io/keycloak/keycloak` |
| **Keycloak / security** | Delete `app/config/security/`, the three oauth2/security starters, and `spring.security.oauth2.*` props. All endpoints become public; adjust `AuditorAwareImpl` (or drop `@EnableR2dbcAuditing`) and the `Principal` parameters in controllers; the permissive `integration`-profile `SecurityConfig` under `src/test/` can also go |
| **Permission slice** | Delete `app/permission/`, its changelog `db/changesets/changelog-001.yml`, and its tests/fixtures — then copy the layout for your own entity. Note that `IntegrationTest.kt` mixes slice, gRPC, and external-HTTP coverage, so trim it there as well |
| **HTTP samples** | Delete `sample/http/` and `com.softeno.external.*` props (note: `ExternalController` also demonstrates the gRPC client) |
| **Circuit breaker** | Delete the `spring-cloud-starter-circuitbreaker-*` dependency + `springCloudVersion` BOM, `sample/http/config/CircuitBreakerConfig.kt`, and the `rcb.run` wrapper in `ExternalController.kt` |
| **OpenTelemetry** | Delete `app/config/opentelemetry/`, the otel starters + logback appender dep, OTel appender in `logback-spring.xml`, and `management.otlp/tracing/logging.*` props. **Cross-references:** remove the `io.micrometer.tracing.Tracer` injection and trace-debug lines in `app/permission/api/PermissionController.kt` — or just run the `local` profile to disable export without removing anything |
| **Swagger** | Delete the two `springdoc` deps and `springdoc.*` props |

Apart from the flagged cross-references, nothing else in the app depends on the removed pieces, so each deletion leaves a compiling, runnable service.

## Build, lint & test

| Goal               | Command                                   |
|--------------------|-------------------------------------------|
| Full build         | `./gradlew clean build`                   |
| Run all tests      | `./gradlew test`                          |
| Run single test    | `./gradlew test --tests "com.softeno.template.app.permission.service.PermissionServiceTest"` |

(`AGENTS.md` also describes detekt/spotless/jacoco conventions; those plugins are not currently wired into `build.gradle` — add them if you want the lint tasks.)

Integration tests spin up PostgreSQL and Kafka via **Testcontainers**, drive the API with **WebTestClient**, and stub outbound HTTP with **WireMock** — so `./gradlew test` needs only Docker, not the compose stack.

## Docker image

```bash
./gradlew bootJar
docker build -t spring-reactive-r2dbc-template .
```

The [`Dockerfile`](Dockerfile) runs the fat JAR on `eclipse-temurin:24-jre` with the `docker` profile. On pushes to `main`/`release/*`, CI builds and pushes the image (`linux/amd64` + `linux/arm64`) to Docker Hub automatically.

## Notes

- The main class is still named `SoftenoMvcJpaApp` for historical reasons — it is a reactive/R2DBC app; rename it when you rename the package.
- Dev-tooling conveniences are on by default (verbose R2DBC logging, `devtools`, all actuator endpoints); trim them for production use.
- [`tunnels/`](tunnels) holds an optional SSH-tunnel script/config for debugging a remote deployment.
- [`AGENTS.md`](AGENTS.md) documents the conventions and workflow for automated coding agents on this repo.
- [`LICENSE.md`](LICENSE.md) — MIT License.
