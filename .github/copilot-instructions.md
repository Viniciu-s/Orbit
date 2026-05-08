# Orbit — Copilot Instructions

## Project Context

**Orbit** is a zero-dependency Java 21 Event Bus library. Its purpose is to decouple application modules via publish/subscribe messaging. All design decisions must serve this goal.

## Core Principles (always apply)

1. **Spec-driven**: The README.MD is the source of truth. Every implementation must trace back to it.
2. **TDD**: Write tests first. Red → Green → Refactor. Never implement without a failing test.
3. **Zero dependencies**: No Spring, no Guava, no Apache Commons. Only SLF4J (API) + JUnit5 + Mockito (test).
4. **Thread-safe by default**: Assume all public API is called concurrently.
5. **Isolated failures**: One listener failure must never affect other listeners or the caller.

## Harness Flow

When asked to implement anything in Orbit, follow this order:
1. **Anchor** — identify the spec in README.MD
2. **Design** — plan classes, interfaces, signatures
3. **Test first** — write failing tests
4. **Implement** — make tests green
5. **Validate** — verify against README + run `./mvnw verify`

## Agent

For full harness-guided implementation sessions, use the **Orbit Coder** agent (`@orbit-coder`).
