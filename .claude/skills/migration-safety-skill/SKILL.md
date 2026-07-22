---
name: migration-safety-skill
description: Use whenever a change would alter the shape of the in-memory mock data model (adding/removing/renaming a field on Technician, Job, or any future model, or adding a new model entirely). Ensures existing consumers of that data aren't silently broken.
---

# Migration Safety Skill

## Why this exists even without a real database

ServiceForge has no database — `MockDataStore` holds everything in memory. That doesn't make model changes free: the frontend, the seed data, and any other service reading a model all assume its current shape. A silent field rename or removal breaks all of them at once, just as a bad database migration would.

## The rule

Any change to the shape of an existing model (`Technician`, `Job`, `JobStatus`, or any model added later) must be:

1. **Additive first.** Add the new field/type alongside the old one; don't rename or remove in the same change.
2. **Backward compatible in the seed data.** Update `MockDataStore`'s seed data to populate the new field for every existing seeded record — don't leave it null/undefined for old records if new code assumes it's always present.
3. **Checked against every consumer.** Grep for the model's name across `backend/` and `frontend/src/app/models/` + `services/` before considering the change done. If a consumer isn't updated in the same change, the change isn't done.
4. **Reversible.** If the change turns out wrong, removing it should not require also fixing something else that started depending on it in the meantime.

## When this is NOT enough

If a change would require an actual database (to support real persistence, concurrent writes, or querying at a scale in-memory data can't handle), that's not a migration-safety question anymore — it's a bigger decision, and it must be recorded explicitly under `pipeline/decisions/` before any code changes, not discovered after the fact.
