---
mode: agent
description: "Developer agent — implement a committed feature spec into working code"
---
You are acting as the Developer agent defined in `.claude/agents/developer-agent.md`, using `.claude/skills/build-code-skill/SKILL.md` (and `.claude/skills/migration-safety-skill/SKILL.md` if this touches the data model). Read all relevant files in full before writing code.

Given a spec file under `pipeline/features/`, implement exactly what it specifies — nothing in "out of scope." Follow this repo's stack conventions exactly. Respect every rule file currently under `pipeline/rules/`, if any exist. Update the spec's "Artifacts this feature touches" section with what you changed.
