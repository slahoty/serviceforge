# AGENTS.md — ServiceForge

This file is the entry point any coding agent (Claude Code, GitHub Copilot, Codex CLI, or equivalent) should read first when working in this repository. It is intentionally tool-agnostic — every tool-specific file in this repo (`.claude/`, `.github/`) points back to it and to `pipeline/orchestration.md`.

## What this repo is

**ServiceForge** is a Field Service Management platform: work orders, technician scheduling, parts inventory, a customer portal, SLA tracking, mobile dispatch. This boilerplate ships with **Feature 1 — Technician Availability Calendar** already built, as the worked reference for everything built on top of it.

- **Backend:** Java 17, Spring Boot 3, in-memory mock data (no real database — this is a training boilerplate, not a production system).
- **Frontend:** Angular, calling the backend REST API, mock data end to end.

## How to run it

```bash
# Backend (from /backend)
mvn spring-boot:run          # serves http://localhost:8080

# Frontend (from /frontend)
npm install
npm start                    # serves http://localhost:4200
```

## The one rule every agent follows here

**"Develop this feature" is a standing instruction, not a one-off prompt.** Whenever anyone (human or agent) says "develop this feature: <intent>", the work must go through the pipeline defined in **[`pipeline/orchestration.md`](pipeline/orchestration.md)** — BA → Developer → Tester, in that order, with the relevant skills and rules loaded dynamically. Do not skip straight to writing code.

## Where things live

| What | Where |
|---|---|
| The orchestration pipeline (tool-agnostic, read this first) | `pipeline/orchestration.md` |
| Agent role definitions (BA / Developer / Tester) | `.claude/agents/*.md` — plain markdown with a small YAML header for Claude Code; readable as-is by any tool |
| Skills (reusable how-to knowledge) | `.claude/skills/*/SKILL.md` |
| Feature specifications (one file per feature — see the [feature-spec convention](pipeline/features/feature-1-technician-availability.md)) | `pipeline/features/feature-N-<slug>.md` |
| Decisions made while building a feature (what later features must remember) | `pipeline/decisions/feature-N-decisions.md` |
| Project-wide rules | added under `pipeline/rules/` as they're written — none exist yet; the first one gets created the first time a shipped bug needs a standing rule against recurring |

## Coding conventions

- **Backend (Java/Spring Boot):** package-by-layer (`model`, `data`, `service`, `controller`, `dto`); constructor injection, no field injection; REST endpoints return `ResponseEntity<T>`; validation errors return `400` with an `ApiError` body; not-found returns `404` with an `ApiError` body.
- **Frontend (Angular):** one component per screen area under `src/app/`; HTTP calls isolated in `services/`; no business logic in components — components render and delegate.
- **Both:** no real database, no external services — mock/in-memory data only, seeded at startup. Do not introduce a database dependency without an explicit decision recorded in `pipeline/decisions/`.

## Cross-tool notes

- **Codex / any AGENTS.md-aware CLI agent:** you're already reading the right file. Go to `pipeline/orchestration.md` next.
- **GitHub Copilot:** see `.github/copilot-instructions.md` and the reusable prompt files in `.github/prompts/`.
- **Claude Code:** see `CLAUDE.md` — it imports this file and adds the subagent/skill wiring specific to Claude Code.
