---
name: spec-generation-skill
description: Use whenever turning a raw feature intent into a committed feature-spec file under pipeline/features/. This is the BA agent's primary skill — load it before writing any spec.
---

# Spec Generation Skill

## When to use this

Anytime a "develop this feature" instruction arrives with only a one-line intent, and no spec file yet exists for it under `pipeline/features/`.

## The template (must be followed exactly)

```markdown
# Feature N — <Name>

| Field | Value |
|---|---|
| **Status** | Candidate-built / in progress |
| **One-line intent** | <one sentence, nothing else> |
| **Depends on** | <a named decision from a specific prior feature's decision log, or "None"> |
| **In scope** | <what this build must do> |
| **Out of scope** | <what it must NOT do> |
| **Definition of Done** | <an objective, testable checklist> |

## Artifacts this feature touches
<filled in later, by the developer and tester agents>
```

## How to fill in "Depends on" correctly

Do not write "the previous feature" or "Feature N-1" in general terms. Open that feature's file under `pipeline/decisions/` and name the *specific* decision this new feature needs — the same way `pipeline/decisions/feature-1-decisions.md` names the 45-minute travel-buffer decision by value, not just by feature.

## How to fill in "Out of scope" correctly

Ask: what is the next, obviously-related feature someone might be tempted to fold into this one? Name it explicitly as out of scope. This is what keeps two related features (e.g. reserving a part vs. alerting when that part runs low) from silently becoming one over-scoped build.

## How to fill in "Definition of Done" correctly

Every item must be something the tester agent could write one test against. If an item can't be phrased as a pass/fail check, it isn't ready to go in the spec yet — ask a clarifying question instead of guessing.
