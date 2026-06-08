# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Layout

This is a **single-context** repo.

Before exploring, read:

- **`CONTEXT.md`** at the repo root for the Market Data Service glossary and domain language.
- **`docs/adr/`** for architectural decisions relevant to the area being changed, if present.

If ADRs do not exist yet, proceed silently. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## Use the glossary's vocabulary

When output names a domain concept — in an issue title, refactor proposal, hypothesis, or test name — use the term as defined in `CONTEXT.md`.

Do not drift to synonyms the glossary explicitly avoids. For example, prefer terms such as `Financial Instrument`, `Listing`, `Listing Symbol`, `Venue`, `Observed Price`, `Latest Price`, and `Preferred Listing` where appropriate.

If the concept needed is not in the glossary yet, either reconsider the wording or note the gap for `/grill-with-docs`.

## Flag ADR conflicts

If output contradicts an existing ADR, surface it explicitly rather than silently overriding it.
