# Copilot instructions — ServiceForge

Read `AGENTS.md` at the repo root first — it is the tool-agnostic entry point every tool in this repo shares. This file only adds Copilot-specific pointers.

## The pipeline

Any "develop this feature: `<intent>`" request must go through the three-step pipeline in `pipeline/orchestration.md` (BA → Developer → Tester). Do not jump straight to writing code for a new feature.

## Role definitions

The BA, Developer, and Tester roles are defined in full under `.claude/agents/*.md`. They were written in Claude Code's subagent format (a small YAML header, then the role body) but the content is plain markdown — read and follow them the same way you'd follow any instruction file. Reusable slash-command versions of the same three roles are in `.github/prompts/` if you'd rather invoke them that way.

## Skills

Load `.claude/skills/build-code-skill/SKILL.md` before writing backend or frontend code — it has the package layout, DI style, REST conventions, and Angular conventions this repo expects. `.claude/skills/spec-generation-skill/SKILL.md` and `.claude/skills/migration-safety-skill/SKILL.md` apply to the BA role and to data-model changes respectively.

## Conventions to always respect

- No real database — mock/in-memory data only (`MockDataStore` on the backend).
- Constructor injection only on the backend; no HTTP calls inside Angular components, always through a service.
- Every feature has exactly one spec file under `pipeline/features/`, never merged with another feature's spec.
