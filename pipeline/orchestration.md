# The "develop this feature" pipeline

This is the single, tool-agnostic description of how any feature in this repo gets built. It exists so the same workflow can be followed whether you're driving it from Claude Code, GitHub Copilot, Codex, or reading it yourself. Every tool-specific file in this repo (`CLAUDE.md`, `.github/copilot-instructions.md`, `AGENTS.md`) points here.

## The trigger

Anyone — human or agent — can kick this off with a single instruction:

> **"Develop this feature: `<intent>`"**

That one line is the only input.

## Artifact map

Each step reads the step before it and writes its own artifact. Every step but the architect writes a single file; the architect writes a matched pair — the design and its ADRs.

| Step | Agent | Reads | Writes |
|---|---|---|---|
| 1 | BA | intent + all prior features and decisions | `pipeline/features/feature-N-<slug>.md` |
| 2 | Architect | approved spec + all decisions + prior designs (+ review findings on a re-run) | `pipeline/architecture/feature-N-<slug>-design.md` + `pipeline/decisions/feature-N-<slug>-adr.md` |
| 3 | Architecture Review | spec + design + ADRs + prior reviews | `pipeline/reviews/review-N-r<K>.md` |
| 4 | Planner | spec + approved design + ADRs + approving review + rules | `pipeline/plans/plan-N-<slug>.md` |
| 5 | Developer | plan + spec + design + ADRs + rules | code |
| 6 | Tester | plan + spec | tests + results |

Steps 2 and 3 form a loop. **The pipeline cannot pass step 3 without an `APPROVED` verdict.**

## Directory layout

```
pipeline/
  orchestration.md          this file
  features/                 BA output — one file per feature
  architecture/             architect output — one design file per feature
  decisions/                prior-feature decision logs + architect ADR files (feature-N-<slug>-adr.md)
  reviews/                  review output — one file per revision
  plans/                    planner output — one plan per feature
  rules/                    standing rules, written after shipped bugs
```

## The steps

### 1 — BA (turns intent into a spec)

- **Role definition:** `.claude/agents/ba-agent.md`
- **Skill it loads:** `.claude/skills/spec-generation-skill/SKILL.md`
- **What it does:** takes the raw intent, asks what it needs to, and commits `pipeline/features/feature-N-<slug>.md`.
- **What it must check first:** every existing file under `pipeline/features/` and `pipeline/decisions/`, so the new spec correctly names what it depends on instead of guessing.
- **Dependency citation:** the `Depends on` cell must carry a decision **ID** (`D-1.3`) that exists in the corpus, plus a one-clause restatement. Quoted prose without an ID is a failed spec. If the restatement has to argue that the prior decision *anticipates* this intent, the decision does not carry it — the BA halts.
- **Output:** one committed spec file. Nothing gets built yet.
- **If it halts:** `STATUS: BLOCKED`, no file. **Terminal** for step 1. Surface the questions to the human and stop. Do not re-invoke, do not proceed to step 2, do not substitute your own answers. Resume only after the human replies.

**Human checkpoint.** A human approves the spec before step 2. Specs are cheap to correct; designs and code are not.

### 2 — Architect (turns the spec into a design and ADRs)

- **Role definition:** `.claude/agents/architect-agent.md`
- **Inputs:** the approved spec, `pipeline/decisions/`, every prior design. On a re-run, additionally `pipeline/reviews/review-N-r<K>.md`.
- **Output:** the design file at revision `r<K>` plus the ADR file, whose decisions are addressable as `D-N.1`, `D-N.2`, ….
- **Spec authority:** the architect uses the spec's identifiers verbatim. If it believes a field name, status code or data model in the spec is wrong, it **halts** rather than designing the better version. A silently improved design still traces every AC in its own table while leaving the tester nothing to assert against.
- **Revision numbering:** first run is `r1`; each run after `CHANGES_REQUESTED` increments. The design file is overwritten; the `Revision` field and Revision Log carry the history.
- **If it halts:** `STATUS: BLOCKED` is terminal. Surface and stop. **Do not auto-retry** — a BLOCKED architect run means the spec is underspecified, and retrying will pressure the agent into inventing the missing constraint. This is different from `CHANGES_REQUESTED`, which *is* auto-retried.

### 3 — Architecture Review (the gate)

- **Role definition:** `.claude/agents/architecture-review-agent.md`
- **Inputs:** the spec, the design at revision `rK`, the ADR file, every prior review for this feature.
- **Output:** `pipeline/reviews/review-N-r<K>.md`, whose `VERDICT` is exactly `APPROVED` or `CHANGES_REQUESTED`.
- **This step runs after every architect run, including remediation runs.** There is no path from architect to planner that skips it.

## The architect ↔ review loop

This is the core control structure. The orchestrator implements it exactly as written.

```
K = 1
run architect (fresh)
  → BLOCKED?  → surface to human, STOP.
loop:
  run architecture-review at revision rK
    → BLOCKED?              → surface to human, STOP.
    → VERDICT: APPROVED     → exit loop, go to step 4.
    → VERDICT: CHANGES_REQUESTED:
        K = K + 1
        if K > 4            → escalate to human, STOP.
        run architect with review-N-r(K-1).md as input
          → BLOCKED?        → surface to human, STOP.
        continue loop
```

### Loop rules the orchestrator must not violate

1. **`CHANGES_REQUESTED` is not a failure.** It is the normal, expected signal to re-run the architect. Re-run automatically, without asking the human, and without your own commentary on whether the findings are fair.

2. **Never proceed on `CHANGES_REQUESTED`.** The planner is not invoked, the developer is not invoked, no code is written, for as long as the latest verdict is `CHANGES_REQUESTED`. There is no override — no "the findings are only about naming", no "the blocker is arguably out of scope". The verdict is binding.

3. **Never review a stale design.** Each review reads the design at the revision the architect just produced. If the `Revision` field does not match, the review agent halts and so do you.

4. **Never skip the review after a re-run.** Revision `r3` gets `review-N-r3.md` just as `r1` got `review-N-r1.md`.

5. **Never edit an artifact to clear a finding yourself.** The orchestrator routes; it does not design, review or patch. If you find yourself opening the design to add a missing field, stop — that is the architect's run.

6. **Never re-run the review to get a different verdict.** One review per revision. After `CHANGES_REQUESTED`, the next thing that runs is the architect, not the reviewer again.

7. **Distinguish `BLOCKED` from `CHANGES_REQUESTED`.** `BLOCKED` from either agent is terminal and needs a human. `CHANGES_REQUESTED` is a loop iteration and needs the architect. Auto-retrying a `BLOCKED` run defeats its gate; escalating a `CHANGES_REQUESTED` defeats the loop's purpose.

8. **Escalation bound.** If revision `r5` would be needed — four review rounds without approval — stop and escalate with all four review files. Persistent non-convergence means the spec is wrong, or the two agents disagree on something neither can resolve. Do not raise the bound, and do not resolve it by instructing the reviewer to be more lenient.

9. **A spec defect surfaced by the loop routes to step 1, not around it.** If the reviewer's finding is that the design cannot satisfy an AC as written *and the architect halts saying the spec is at fault*, the fix is a BA re-run with the human's answer — not a reviewer who relaxes, and not an architect who substitutes.

### What the human sees

- On approval: nothing required. The pipeline continues to step 4.
- On escalation at `K > 4`: the spec, the latest design, and every review file, findings that never cleared highlighted.
- On any `BLOCKED`: the BLOCKED block verbatim, unedited.

### 4 — Planner (turns the approved design into a plan)

- **Role definition:** `.claude/agents/planner-agent.md`
- **Precondition:** the highest-numbered review for feature `N` has `VERDICT: APPROVED`, and the design's revision matches it. The orchestrator verifies this before invoking; the planner verifies it again.
- **Output:** `pipeline/plans/plan-N-<slug>.md` — atomic tasks, sequenced into waves, each traced to a design element and an AC.
- **If it halts with `REASON: design gap`:** the one case where the orchestrator re-enters the architect loop after an approval. Re-run the architect with the planner's halt report as input, producing revision `r(K+1)`, then run the review again as normal. Do not re-run the planner against the same design, and do not fill the gap yourself.
- **Any other planner halt:** terminal, surface to the human.

### 5 — Developer (turns the plan into code)

- **Role definition:** `.claude/agents/developer-agent.md`
- **Skills it loads:** `.claude/skills/build-code-skill/SKILL.md` and, if the change touches the data model, `.claude/skills/migration-safety-skill/SKILL.md`.
- **Rules it must follow:** everything under `pipeline/rules/*.md` that exists at the time.
- **What it does:** executes the plan wave by wave, without expanding scope beyond what the plan and spec say.
- **Wave discipline:** every task in wave `W` passes its completion check before any task in wave `W+1` starts.
- **Output:** working code, committed.

### 6 — Tester (checks the code against the spec)

- **Role definition:** `.claude/agents/tester-agent.md`
- **What it does:** writes and runs tests against the Definition of Done in the spec and the completion checks in the plan — not against what the code happens to do, but against what it was supposed to do.
- **Output:** test results. If they fail, the loop returns to **step 5**, not step 1 and not step 2 — the spec doesn't change because the code didn't meet it, and neither does the approved design.
- **Exception:** if a failure is caused by the design being unimplementable as approved, that is a design gap. Route to step 2 and run the full loop again from `r(K+1)`. This should be rare — the review checklist exists to catch it earlier.

## Safe recovery

If any step produces something that doesn't match its input (a spec with no Definition of Done, a design that doesn't cover every AC, a review with no verdict, a plan whose tasks don't trace, code that doesn't match the plan), stop and surface it to a human rather than proceeding on a guess. This pipeline has no silent fallback.

The one thing that is *not* a safe-recovery event is a `CHANGES_REQUESTED` verdict. That is the pipeline working.

## Human checkpoints

- **After step 1** — a human approves the spec before design starts.
- **After step 3, only if the loop escalates** at `K > 4`.
- **On any `STATUS: BLOCKED`** from any agent.

Everything else runs unattended.

## Running this in each tool

- **Claude Code:** the six role files under `.claude/agents/` are real subagents. Say "develop this feature: `<intent>`" and Claude Code routes through them in order, including looping steps 2 and 3 until approval.
- **GitHub Copilot:** use the matching prompt files in `.github/prompts/` as slash commands, in the order above. Copilot will not loop on its own — re-issue `/architect-agent` then `/architecture-review-agent` yourself until the verdict is `APPROVED`.
- **Codex / any `AGENTS.md`-reading CLI agent:** `AGENTS.md` points here. The agent reads the six role files as plain markdown (the YAML header is harmless to ignore) and follows the same steps, including the loop.
- **Anyone without an agent tool:** the six role files are readable specs. Do the steps yourself, in order, and hold the loop honestly — the temptation to approve your own design on r2 is exactly what the separate review role exists to resist.
