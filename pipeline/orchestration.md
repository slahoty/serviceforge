# The "develop this feature" pipeline

This is the single, tool-agnostic description of how any feature in this repo gets built. It exists so the same workflow can be followed whether you're driving it from Claude Code, GitHub Copilot, Codex, or reading it yourself. Every tool-specific file in this repo (`CLAUDE.md`, `.github/copilot-instructions.md`, `AGENTS.md`) points here.

## The trigger

Anyone — human or agent — can kick this off with a single instruction:

> **"Develop this feature: `<intent>`"**

That one line is the only input. Everything else below is how it gets turned into a shipped feature.

## The three steps

### 1 — BA (turns intent into a spec)

- **Role definition:** `.claude/agents/ba-agent.md`
- **Skill it loads:** `.claude/skills/spec-generation-skill/SKILL.md`
- **What it does:** takes the raw intent, asks what it needs to (scope, dependencies on prior features, definition of done), and commits a new file at `pipeline/features/feature-N-<slug>.md` following the same template as `pipeline/features/feature-1-technician-availability.md`.
- **What it must check first:** every existing file under `pipeline/features/` and `pipeline/decisions/`, so the new spec correctly names what it depends on instead of guessing.
- **Output:** one committed spec file. Nothing gets built yet.

### 2 — Developer (turns the spec into code)

- **Role definition:** `.claude/agents/developer-agent.md`
- **Skills it loads:** `.claude/skills/build-code-skill/SKILL.md` (stack conventions) and, if the change touches the data model, `.claude/skills/migration-safety-skill/SKILL.md`.
- **Rules it must follow:** everything under `pipeline/rules/*.md` that exists at the time (there are none yet — the first one gets written the first time a shipped bug needs a standing rule against recurring).
- **What it does:** implements the spec from step 1, in the backend and/or frontend as the spec requires, without expanding scope beyond what the spec says.
- **Output:** working code, committed.

### 3 — Tester (checks the code against the spec)

- **Role definition:** `.claude/agents/tester-agent.md`
- **What it does:** writes and runs tests against the Definition of Done in the spec from step 1 — not against what the code happens to do, but against what it was supposed to do.
- **Output:** test results. If they fail, the loop returns to step 2, not step 1 — the spec doesn't change because the code didn't meet it.

## Safe recovery

If any step produces something that doesn't match its input (a spec with no Definition of Done, code that doesn't match the spec, tests that can't run), stop and surface that to a human rather than proceeding on a guess. This pipeline has no silent fallback.

## Human checkpoints

A human should look at the output of step 1 (the spec) before step 2 starts — specs are cheap to correct, code is not. Everything else can run unattended unless something trips the safe-recovery condition above.

## Running this in each tool

- **Claude Code:** the three role files under `.claude/agents/` are real Claude Code subagents (they have the frontmatter Claude Code expects). Say "develop this feature: `<intent>`" and Claude Code should route through them in order, per this document.
- **GitHub Copilot:** use the matching prompt files in `.github/prompts/` (`ba-agent.prompt.md`, `developer-agent.prompt.md`, `tester-agent.prompt.md`) as slash commands, in the order above, or paste this document into a Copilot Chat session as context.
- **Codex / any `AGENTS.md`-reading CLI agent:** `AGENTS.md` already points here. Give the same "develop this feature" instruction; the agent should read the three role files under `.claude/agents/` as plain markdown (the YAML header is harmless to ignore) and follow the same three steps.
- **Anyone without an agent tool:** the three role files are readable specs. Do the three steps yourself, in order.
