## Agent skills

### Issue tracker

Issues live in GitHub Issues for `lorenzo-amprino/Market-Data-Service`; use the `gh` CLI. See `docs/agents/issue-tracker.md`.

### GitFlow

Every issue must be developed on its own branch and completed through a Pull Request. Use `feature/<issue-number>-<issue-name>` for features and `bug/<issue-number>-<issue-name>` for bug fixes. See `docs/agents/gitflow.md`.

### Development guidelines

When implementing issues, follow TDD by default and apply the repository's code quality guidelines: KISS, YAGNI, DRY, pragmatic SOLID, behavior-focused tests, and domain/ADR consistency. See `docs/agents/development.md`.

### Triage labels

Use the default triage label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context repo: read root `CONTEXT.md` and relevant ADRs under `docs/adr/`. See `docs/agents/domain.md`.
