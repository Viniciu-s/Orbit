---
description: "Use when writing, reviewing, or modifying any Java source file in the Orbit Event Bus library. Covers coding standards, architecture decisions, thread-safety patterns, and API design rules for Orbit."
applyTo: "src/**/*.java"
---

# Orbit Java Coding Standards

## Architecture

Orbit has four core components — every class must belong to exactly one:

| Component | Package | Responsibility |
|-----------|---------|---------------|
| `Event` | `api/` | Represents a domain occurrence — immutable data carrier |
| `EventListener` | `api/` | Functional interface for reacting to an event |
| `EventBus` | `api/` + `core/` | Public contract + implementation |
| `Dispatcher` | `core/` | Internal routing, thread management, error isolation |

## Java Standards

- **Java version**: 21. Use records, sealed classes, `var`, switch expressions, pattern matching where they add clarity.
- **Events**: Prefer `record` for immutable event objects.
- **Listeners**: Must be `@FunctionalInterface` — single abstract method.
- **No nulls in the public API**: validate at boundary, throw `NullPointerException` with message via `Objects.requireNonNull`.
- **Logging**: Use SLF4J only. Never `System.out.println` or `System.err`.
- **No framework dependencies**: zero Spring, zero Guava, zero Apache Commons.

## Thread Safety Rules

- Listener registries: use `ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventListener>>`.
- Async dispatch: use `ExecutorService` (injected or default `Executors.newCachedThreadPool()`).
- `shutdown()` must call `executor.shutdown()` gracefully.
- Assume all public methods can be called from multiple threads simultaneously.

## Error Handling

- Listener execution MUST be wrapped in try-catch inside the Dispatcher.
- A failing listener logs the error via SLF4J and continues — never propagates to the caller.
- Example pattern:
  ```java
  try {
      listener.onEvent(event);
  } catch (Exception e) {
      log.error("Listener {} failed for event {}: {}", listener, event.getClass().getSimpleName(), e.getMessage(), e);
  }
  ```

## Naming Conventions

- Events: noun + past tense — `UserCreatedEvent`, `PaymentApprovedEvent`
- Listeners: noun + "Listener" — `EmailListener`, `AuditListener`
- Internal classes: prefixed with `Orbit` — `OrbitEventBus`, `OrbitDispatcher`
- Factory method: `Orbit.create()` — fluent builder pattern

## Testing Standards

- One test class per production class.
- Test method names: `should_<behavior>_when_<condition>` — e.g., `should_notifyListener_when_eventPublished`.
- Use `@ExtendWith(MockitoExtension.class)` for unit tests with mocks.
- Integration-style tests (no mocks) go in a separate class suffixed `IntegrationTest`.
- Cover: happy path, null inputs, concurrent publish, error isolation.

## What NOT to do

- Do NOT add `@Component`, `@Bean`, or any Spring annotations.
- Do NOT use checked exceptions in the public API — use unchecked only.
- Do NOT expose internal implementation classes — keep them package-private or in `core/`.
- Do NOT change `pom.xml` dependencies without explicitly discussing trade-offs.
