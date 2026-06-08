# Development Guidelines for Agents

These guidelines apply when an agent implements a GitHub issue in this repository.

## Default workflow

- Start from an up-to-date `main` branch.
- Develop every issue on its own branch, following `docs/agents/gitflow.md`.
- Read `CONTEXT.md` and the relevant ADRs under `docs/adr/` before changing code.
- Keep the implementation inside the issue scope. Do not add speculative features.
- Open a Pull Request when done; do not merge directly to `main`.

## TDD as the default

Use test-driven development for features and bug fixes unless the issue is documentation-only or pure configuration.

Preferred loop:

1. Identify one externally observable behavior.
2. Write one failing test for that behavior.
3. Implement the smallest production change that makes it pass.
4. Repeat for the next behavior.
5. Refactor only when tests are green.

Avoid writing all tests first and all production code afterwards. Prefer vertical tracer bullets that prove one path end-to-end.

## Testing principles

Good tests verify behavior through public interfaces, not implementation details.

Prefer:

- API/integration tests for service behavior.
- Repository contract tests for persistence guarantees that matter externally.
- Application/domain tests for richer policies and aggregate behavior.
- Testcontainers PostgreSQL for database integration tests.

Avoid:

- Testing private methods.
- Asserting internal call counts or method order.
- Mocking internal collaborators just to match implementation structure.
- Querying the database directly to verify behavior when a public read interface exists.
- Brittle tests that fail on harmless refactors.

Use mocks/stubs mainly at external boundaries, such as Data Provider adapters, clocks, network clients, or unavailable infrastructure.

## Code quality principles

Apply these principles pragmatically, not mechanically.

### KISS

Prefer the simplest design that satisfies the issue and tests. Avoid frameworks, abstractions, or patterns that are not yet justified by current behavior.

### YAGNI

Do not implement future capabilities just because they are likely later. If a future extension is obvious, leave a clear seam but keep behavior limited to the issue.

### DRY

Remove meaningful duplication after tests are green. Do not extract abstractions too early; repeated code can be acceptable until the right shape is clear.

### SOLID

Use SOLID principles to keep code understandable and changeable:

- Keep classes focused on one reason to change.
- Depend on interfaces at external boundaries and volatile seams.
- Keep public interfaces small and behavior-oriented.
- Avoid forcing callers to depend on methods they do not use.
- Make dependencies injectable rather than constructing infrastructure deep inside business logic.

Do not use SOLID as a reason to create shallow, noisy abstractions.

## Architecture and domain consistency

- Use the exact domain vocabulary from `CONTEXT.md`: Financial Instrument, Listing, Listing Symbol, Venue, Observed Price, Latest Price, Preferred Listing, Data Provider, Data Availability, Data Provenance, Universe, and related terms.
- Respect existing ADRs. If implementation needs to contradict an ADR, stop and surface the conflict before proceeding.
- Keep Financial Instrument facts separate from Listing facts.
- Keep local read APIs independent from synchronous Data Provider calls.
- Preserve PostgreSQL as the source of truth for canonical market data.
- Do not introduce Redis, streaming, intraday prices, FX conversion, or other out-of-scope v1 capabilities unless the issue explicitly says so.

## Dependency and configuration discipline

- Add dependencies only when they directly support the issue.
- Prefer Spring Boot conventions where they reduce custom code.
- Keep configuration explicit and environment-driven.
- Do not commit secrets, local credentials, generated build output, or provider API keys.
- Document new required environment variables or local commands in `README.md`.

## Database changes

- Use Flyway migrations for schema changes.
- Keep migrations deterministic and reviewable.
- Add tests for important constraints, indexes, and migration behavior where relevant.
- Do not hide domain decisions in migrations without reflecting them in code/docs when needed.

## Pull Request quality bar

Before opening a PR:

- All relevant tests pass locally.
- The code builds from a clean checkout.
- The PR description lists the issue, implementation summary, and verification commands.
- Public API, schema, or configuration changes are documented.
- Any follow-up work is explicit and linked to an issue when appropriate.
