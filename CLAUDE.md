# CLAUDE.md — ServiceForge (Claude Code)

@AGENTS.md

The above import brings in the tool-agnostic project rules. Everything below is Claude-Code-specific.

## Subagents

This repo defines three subagents under `.claude/agents/`, matching the roles in `pipeline/orchestration.md`:

- `ba-agent` — turns a feature intent into a committed spec under `pipeline/features/`.
- `developer-agent` — implements a committed spec into working code, loading the relevant skills and rules.
- `tester-agent` — writes and runs tests against a spec's Definition of Done.

Invoke them the way you'd invoke any Claude Code subagent, e.g.:

```
"Develop this feature: <intent>" 
→ Claude Code should route this through ba-agent, then developer-agent, then tester-agent,
  per pipeline/orchestration.md — not write code directly.
```

## Skills

Skills live under `.claude/skills/<skill-name>/SKILL.md` (standard Claude Agent Skills format) and are loaded automatically when relevant:

- `build-code-skill` — backend + frontend stack conventions for this repo.
- `spec-generation-skill` — how the BA agent turns intent into a feature spec.
- `migration-safety-skill` — how to evolve the mock data model without breaking existing consumers (there is no real database, but the same discipline applies to the in-memory model).

## Memory note for this repo

Feature-by-feature decisions (the facts a later feature may need to retrieve) are logged under `pipeline/decisions/`, one file per feature — starting with `pipeline/decisions/feature-1-decisions.md`. Do not rely on conversation history alone to carry these facts forward; they are committed here specifically so they can be retrieved on demand instead of kept in context permanently.
