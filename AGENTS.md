# AGENTS.md – Guidance for Automated Coding Agents

---
## Table of Contents

### Workflow Guidelines
- **Clarify Requirements First**: Before starting any change, ensure the requirement is fully understood. Ask the user follow‑up questions if anything is ambiguous. Multiple clarification questions are allowed.
- **Obtain Acceptance of Action Plan**: Always draft a **detailed, verbose implementation plan** (steps, design decisions, test strategy) and present it to the user. Do **not** begin any code changes until the user explicitly approves the plan.
- **Test‑Driven Development (TDD) Cycle**:
  1. Write comprehensive unit/integration tests following project conventions.
  2. Implement the minimum production code needed to satisfy the tests.
  3. Refactor while keeping all tests green.
  4. Repeat – add more tests, expand implementation, ensure clean, modular code.
- **Never Cut Corners**: Do not add dummy implementations merely to make tests pass. Follow SOLID, Open‑Closed, KISS, and hexagonal architecture principles.
- **Modular Design**: Keep new code modular, well‑encapsulated, and adhere to the existing layered architecture.
- **Documentation & Review**: Update relevant documentation, comments, and ensure code quality matches a senior developer’s standards.


1. [Project Overview](#project-overview)
2. [Build, Lint & Test Commands](#build-lint--test-commands)
3. [Running a Single Test](#running-a-single-test)
4. [Code Style & Conventions](#code-style--conventions)
   - Imports
   - Formatting & Layout
   - Types & Nullability
   - Naming Conventions
   - Error Handling
   - Reactive & Coroutine Practices
   - Architecture & Design Patterns
5. **Testing Guidelines**
6. **Performance & Back‑pressure**
7. **Commit & PR Best Practices**
8. **Additional Resources**
---
## Project Overview
- **Preference**: Prefer Kotlin coroutines over Project Reactor for asynchronous handling. Agents should convert reactive streams to coroutines where feasible, while maintaining back‑pressure support.
- **Language**: Kotlin 1.9 (JVM target)
- **Framework**: Spring Boot 4.x (reactive stack)
- **Database**: R2DBC (PostgreSQL) with Flyway migrations
- **Messaging**: Kafka (reactive client)
- **Testing**: JUnit 5, Kotest, MockK, Spring Test, WebTestClient, Testcontainers
- **Build Tool**: Gradle 8 (Kotlin DSL)
- **Containerisation**: Docker & Docker‑Compose (Postgres)

The codebase follows a **hexagonal / clean‑architecture** flavour – core domain logic lives in `src/main/kotlin/com/softeno/template/app/...` and adapters (web, db, kafka) are separate packages.

---
## Build, Lint & Test Commands
| Goal | Command | Description |
|------|---------|-------------|
| **Compile** | `./gradlew clean build` | Full clean compile, runs tests, creates JAR. |
| **Run Application** | `./gradlew bootRun` | Starts the Spring Boot app (uses `application.properties`). |
| **Package JAR** | `./gradlew bootJar` | Produces an executable JAR in `build/libs`. |
| **Run Checks Only** | `./gradlew check` | Executes linting, static analysis, and tests without building the JAR. |
| **Static Analysis** | `./gradlew detektMain detektTest` | Runs Detekt (Kotlin linter) on source and test code. |
| **Formatting** | `./gradlew spotlessApply` | Applies Spotless formatting (Kotlin + Gradle). |
| **Run All Tests** | `./gradlew test` | Executes unit + integration tests. |
| **Run Integration Tests Only** | `./gradlew integrationTest` *(if defined)* | Use custom source set; otherwise filter with tags. |
| **Run Tests with Coverage** | `./gradlew jacocoTestReport` | Generates a JaCoCo coverage report under `build/reports/jacoco`. |
| **Run a Single Test** | See section below. |

---
## Running a Single Test
Kotlin test classes are discovered by the `test` task. To run a single test method or class, use the Gradle `--tests` filter:

```bash
# Run a single test class
./gradlew test --tests "com.softeno.template.app.permission.service.PermissionServiceTest"

# Run a single test method (full qualified name)
./gradlew test --tests "com.softeno.template.app.permission.service.PermissionServiceTest.shouldPersistAndRetrievePermission"
```

*Tip for agents*: When a failing test is identified, re‑run it with the same filter to verify the fix quickly.

---
## Code Style & Conventions
The following rules are **non‑negotiable** for any automated agent. Violations will cause the CI lint step to fail.

### 1. Imports
- **Alphabetical order**, grouped by: `kotlin.*`, `java.*`, `org.*`, `com.*`.
- One blank line between groups.
- Use **single‑type imports** (`import java.util.UUID`) – avoid wildcard `*` imports.
- Remove unused imports – Detekt’s `UnusedImports` rule enforces this.

```kotlin
import com.softeno.template.app.permission.api.PermissionController
import com.softeno.template.app.permission.service.PermissionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
```

### 2. Formatting & Layout
- **Indentation**: 4 spaces, no tabs.
- **Line length**: max 120 characters.
- **Braces**: K&R style – opening brace on same line.
- **Trailing commas** in multi‑line collections/parameter lists (Kotlin 1.9 supports it).
- **Blank lines**: separate logical sections (e.g., imports, class header, properties, functions).
- **Spotless** is the single source of truth – run `./gradlew spotlessApply` before committing.

### 3. Types & Nullability
- Prefer **non‑nullable** types. Use `?` only when the value can legitimately be absent.
- When a nullable value is required, employ **`requireNotNull`** or **`?.let {}`** to avoid NPEs.
- Leverage Kotlin’s **type‑aliases** for complex reactive types, e.g.: `typealias Flux<T> = reactor.core.publisher.Flux<T>` for readability.
- Use **value classes** (`@JvmInline`) for simple wrappers (e.g., `PermissionId`).

### 4. Naming Conventions
| Element | Convention |
|---------|-------------|
| Packages | lower‑case, dot‑separated (`com.softeno.template.app.permission`) |
| Classes / Interfaces | PascalCase (`PermissionService`, `PermissionRepository`) |
| Enums | PascalCase, singular (`PermissionStatus`) |
| Functions | camelCase, verb‑first (`createPermission`, `findById`) |
| Properties | camelCase, descriptive (`permissionId`, `createdAt`) |
| Constants | Upper‑snake (`MAX_RETRY_ATTEMPTS`) |
| Test classes | `<ClassUnderTest>Test` (`PermissionServiceTest`) |
| Test methods | descriptive, `should<Behavior>` (`shouldPersistAndRetrievePermission`) |

### 5. Error Handling
- **Never swallow exceptions**; re‑throw or map to a domain‑specific exception.
- Use **Spring’s `ResponseStatusException`** for HTTP errors, with appropriate status codes.
- For reactive pipelines, prefer **`onErrorMap`** / **`onErrorResume`** rather than `try/catch`.
- Create a **`DomainException`** hierarchy for business‑logic errors.
- Log errors at **WARN** or **ERROR** level with **structured logging** (`log.error("Failed to …", e)`).

### 6. Reactive & Coroutine Practices
- Prefer **Project Reactor** (`Mono`, `Flux`) for public APIs; use **Kotlin coroutines** (`suspend`, `Flow`) for internal services when it simplifies code.
- **Back‑pressure**: never `block()` inside a reactive chain. Use `publishOn`/`subscribeOn` wisely.
- Convert between Reactor and Coroutines with `reactor.kotlin.coroutines.awaitSingle`, `asFlux()`, etc.
- Keep **side‑effects** at the end of the chain; avoid modifying shared state inside `map`.
- **Timeouts**: apply `timeout(Duration)` on external calls (WebClient, R2DBC) to protect the system.

### 7. Architecture & Design Patterns
- Follow **Hexagonal (Ports & Adapters)**: core domain (`service`, `mapper`, `model`) has no Spring annotations; adapters (`controller`, `repository`, `kafka`) implement interfaces defined in the domain.
- Use **Dependency Injection** (constructor injection only). No field injection.
- Apply **SOLID**:
  - **Single Responsibility** – each class does one thing.
  - **Open/Closed** – extend behavior via interfaces, avoid modifying existing classes.
  - **Liskov Substitution** – keep contracts consistent.
  - **Interface Segregation** – small, focused interfaces.
  - **Dependency Inversion** – depend on abstractions.
- **KISS** – avoid over‑engineering; introduce a pattern only when the problem demands it.
- Common OO patterns used:
  - **Factory** (for creating domain objects from DTOs).
  - **Adapter** (e.g., Kafka listener to service).
  - **Decorator** (adding logging or metrics to repositories).
- **Do NOT** introduce a full‑blown DDD layer unless a clear bounded context emerges.

---
## Testing Guidelines
1. **Unit Tests**
   - Focus on **pure business logic** – repository & controller layers are covered by integration tests.
   - Use **MockK** for mocks/stubs; verify interactions only when they affect behavior.
   - Avoid testing getters/setters or trivial data classes.
   - Use **`@Nested`** classes (JUnit) for grouping scenarios.
   - Keep test method names expressive (`shouldReturnPermissionWhenExists`).
2. **Integration Tests**
   - Spin up **Testcontainers** for PostgreSQL & Kafka where needed.
   - Use **`@SpringBootTest`** with `webEnvironment = RANDOM_PORT` and `WebTestClient` for end‑to‑end HTTP flow.
   - Test **error paths**, **validation**, **security** (401/403), and **back‑pressure** behaviour.
   - Do **not** mock Spring beans; rely on the actual context to verify wiring.
3. **Performance / Reactive Tests**
   - Use **`StepVerifier`** with `virtualTime()` for time‑based operators.
   - Verify **`request(n)`** behaviour to ensure back‑pressure compliance.
4. **Coverage**
   - Target **~80%** line coverage; focus on critical paths.
   - Exclude generated code and third‑party libraries from coverage metrics.
5. **Test Data**
   - Use **Builder pattern** or Kotlin `apply {}` blocks for fixture creation.
   - Keep fixtures **immutable**; share via companion objects when appropriate.
6. **CI Recommendations**
   - `./gradlew test` must succeed in CI.
   - `./gradlew detektMain detektTest spotlessCheck` must pass.
   - Failing tests abort the PR.

---
## Performance & Back‑pressure
- **Reactive DB Calls**: always use **R2DBC** non‑blocking APIs; never call `blocking` JDBC.
- **Kafka**: configure **`max.poll.records`** and **`reactor.kafka.receiver.KafkaReceiver`** to respect demand.
- **WebClient**: enable **`connectionPool`** and **`readTimeout`**; reuse the same bean.
- **Circuit Breaker**: use Resilience4j with sensible defaults (`failureRateThreshold=50`, `waitDurationInOpenState=30s`).
- **Metric Collection**: expose **Micrometer** counters for request latency and throughput.
- **Avoid heavy object allocation** in hot paths – reuse immutable data classes.
- **Profiling**: run `./gradlew bootRun --args='--spring.profiles.active=dev'` and attach a profiler (YourKit, VisualVM) for bottleneck analysis.

---
## Commit & PR Best Practices
1. **Atomic Commits** – one logical change per commit.
2. **Commit Message** – `{type}(scope): short description`
   - `type` = `feat`, `fix`, `refactor`, `test`, `chore`.
   - Example: `feat(permission): add bulk‑create endpoint`.
3. **PR Title** – concise, matches first line of commit.
4. **PR Description** – include **What**, **Why**, **How** and **Testing steps**.
5. **No Secrets** – never commit `.env`, API keys, passwords.
6. **Never commit or push without explicit user approval** – agents must obtain clear user confirmation before executing any `git commit` or `git push` commands.
7. **Rebase** – keep a linear history; avoid merge commits.
8. **CI Checks** – ensure `./gradlew check` passes before merging.

---
## Additional Resources
- **Kotlin Coding Conventions** – https://kotlinlang.org/docs/coding-conventions.html
- **Spring Reactive Documentation** – https://docs.spring.io/spring-framework/reference/web/reactive.html
- **Detekt Ruleset** – https://detekt.dev/docs/rules/
- **Hexagonal Architecture** – https://alistair.cockburn.us/hexagonal-architecture/
- **Resilience4j** – https://resilience4j.readme.io/
- **Testcontainers** – https://www.testcontainers.org/
- **Spotless Kotlin Plugin** – https://github.com/diffplug/spotless/tree/main/plugin-gradle#kotlin
---
*Generated by the AI assistant to guide automated agents working on the `spring-reactive-r2dbc-template` repository. Follow these rules strictly to keep the codebase clean, performant, and maintainable.*
