# Architecture Review — Feature 2, revision r1

| Field | Value |
|---|---|
| **Spec** | `pipeline/features/feature-2-job-parts-reservation.md` |
| **Design reviewed** | `pipeline/architecture/feature-2-job-parts-reservation-design.md` (revision r1) |
| **ADRs reviewed** | `pipeline/decisions/feature-2-job-parts-reservation-adr.md` — D-2.1, D-2.2, D-2.3 |
| **VERDICT** | **APPROVED** |
| **Blockers** | 0 |
| **Majors** | 0 |
| **Minors** | 2 |

## Verdict rationale

The design is buildable and every acceptance criterion is assertable from the design alone. All six ACs map to concrete identifiers that appear verbatim (`requiredParts`, `quantityOnHand`, `quantityReserved`, `partId`, and the exact status codes 201/400/404/409). The concurrency criterion AC-2.5 is backed by a named mechanism — a single `synchronized` block owning the whole resolve-check-reserve sequence (D-2.1) — not the word "atomically", so the tester can assert `quantityReserved <= quantityOnHand` deterministically. The all-or-nothing rule is upheld by checking every part before incrementing any, and the design explicitly declines out-of-scope work (no cancellation/release, no restock, no `GET /api/parts`, no parts CRUD UI). The two findings are MINOR clarity points that do not change what gets built.

## AC conformance

| AC | Asserted identifier | Present in design? | Assertable? | Finding |
|---|---|---|---|---|
| AC-2.1 | `201`; `Job.requiredParts` matches request; each `Part.quantityReserved` += requested; `quantityOnHand` unchanged | Yes — §3 `POST /api/jobs` + `reserveForJob`, §4.1 steps 1–7, §7 rows 1–2 | Yes — 201 + injected `MockDataStore.getAllParts()`/`findPart` deltas | F-2.r1.1 (MINOR) — value-equality of `RequiredPart` not stated |
| AC-2.2 | `409`; `ApiError` names every short `partId` + requested + available; no `Job`; no `quantityReserved` change | Yes — §5 S-2.2, §6 rows 1–2, §7 `409` + store-unchanged rows | Yes — 409 + message assertion + store counts | — PASS |
| AC-2.3 | `404`; `ApiError` names unknown `partId`; no `Job`; no `quantityReserved` change | Yes — §5 S-2.3, §7 `404` + store-unchanged rows | Yes — 404 + message + store counts | — PASS |
| AC-2.4 | `201`; empty `requiredParts`; no `quantityReserved` change | Yes — §4.1 step 5 guard, §3 empty-list behaviour, §7 row 1 | Yes — 201 + empty list + store counts | — PASS |
| AC-2.5 | Two concurrent requests → exactly one `201` + reservation, other `409`; `quantityReserved <= quantityOnHand` | Yes — §4.1 atomicity boundary, §6 row 1, §2.3 low-stock seed (part id 3, on-hand 2) | Yes — `synchronized` (D-2.1) makes outcome deterministic | — PASS |
| AC-2.6 | `400`; no `Job`; no `quantityReserved` change | Yes — §3 validation, §5 S-2.6, §7 `400` row | Yes — 400 via `@NotNull @Positive @Valid` + store counts | F-2.r1.2 (MINOR) — data-model table lacks explicit null/constraint columns |

## Findings

### F-2.r1.1 — `RequiredPart` value-equality not stated

- **Severity:** MINOR
- **Location:** §1 (Model `RequiredPart`), §7 row 1 ("`Job.requiredParts` equal to the submitted list")
- **Finding:** AC-2.1 asserts the created `Job`'s `requiredParts` "matches" the submitted list, and §7 phrases this as "equal to the submitted list", but the design does not state whether `RequiredPart` defines value equality (`equals`/`hashCode`) or whether the tester compares field-by-field. Either is fine; leaving it unstated is a small clarity gap that does not change the build.
- **Required to clear:** the design (or the plan derived from it) should state how `Job.requiredParts` equality is asserted — value-equality on `RequiredPart`, or explicit `partId`/`quantity` field comparison. Not required to clear before approval (MINOR); carry into the plan.

### F-2.r1.2 — Data-model constraints given in prose, not a typed field table

- **Severity:** MINOR
- **Location:** §1 Scope-of-change, §2.2 Invariants
- **Finding:** Field types are given (`Long id; String name; int quantityOnHand; int quantityReserved`) and constraints appear as invariants in §2.2, but there is no single field table with explicit nullability/constraint columns per field. All information needed to build is present; this is a presentation MINOR only.
- **Required to clear:** none required for approval; optional consolidation into a typed field table if the architect revises for other reasons.

## Prior findings

*(Omitted — this is r1.)*

## Checklist results

| # | Item | Result |
|---|---|---|
| 1 | Every AC in traceability table, mapped to something concrete | PASS (§10) |
| 2 | Every AC's asserted identifier appears in design verbatim | PASS (see AC conformance) |
| 3 | Every scenario S-2.x has a control-flow path | PASS (§4.1 happy path; §5 failure table covers S-2.2–S-2.6) |
| 4 | Control-flow ordering matches spec (check-before-create) | PASS (§4.1 reserve before `save`; throws leave no `Job`) |
| 5 | Design implements spec's stated failure behaviour and status codes | PASS (400/404/409 per D-2.2, matches spec S-2.2/2.3/2.6) |
| 6 | Design builds nothing in spec's Out of scope | PASS (§1 "Explicitly not touched"; no cancel/restock/CRUD) |
| 7 | Design adds no endpoint/field/defect-fix no AC requires | PASS (frontend `job.model.ts` change is a migration-safety consumer update for the new `Job.requiredParts` field, not new scope; no `GET /api/parts`) |
| 8 | Design does not silently narrow the spec | PASS |
| 9 | Every contract concrete enough to stub without a follow-up | PASS (§3 signatures, request/response, errors) |
| 10 | Every contract enumerates failure responses, not just success | PASS (§3 `POST /api/jobs` lists 400/404/409) |
| 11 | Two distinct failure modes collapsed onto one status code? | PASS — 409 covers time-overlap (F1) and shortage (F2), but the spec itself mandates 409 for shortage (S-2.2, D-2.2) and they are distinguished by `ApiError.message`; spec-sanctioned, assertable |
| 12 | Idempotency stated for every mutating contract | PASS (§3 explicit accepted non-idempotency, A-2.6) |
| 13 | Contracts compose (caller shape matches callee) | PASS (controller maps `RequiredPartRequest`→`RequiredPart`; service consumes `List<RequiredPart>`) |
| 14 | Every field has type, nullability and constraint | PASS with F-2.r1.2 (MINOR) — types present, constraints in §2.2, no per-field null column |
| 15 | Every entity/field the spec names is present by name | PASS (`Part`, `quantityOnHand`, `quantityReserved`, `requiredParts`, `partId`, `quantity`) |
| 16 | Uniqueness/referential requirements enforced by named constraint | PASS (`findPart(Long)` resolution enforces referential check → 404; single central pool) |
| 17 | AC asserting concurrency names the serialising mechanism | PASS (§6 + D-2.1: single `synchronized` block; not merely "atomically") |
| 18 | Design states what the losing request observes under contention | PASS (§5 S-2.5: loser re-reads lower availability → 409) |
| 19 | Every retry bounded, every unbounded wait removed/justified | PASS (no retries introduced; no unbounded waits) |
| 20 | Every failure outcome mechanically observable | PASS (§7 status codes + `ApiError.message` + store counts) |
| 21 | Every non-obvious choice has a D-2.x section | PASS (D-2.1 atomicity, D-2.2 error mapping, D-2.3 additive fields) |
| 22 | Any decision contradicts a prior accepted decision without superseding | PASS (Feature 1 travel-buffer preserved; §1, §6 row 3) |
| 23 | Every decision carries a usable "constrains future features by" clause | PASS (all three D-2.x have it) |
| 24 | Rejected alternatives real, not strawmen | PASS (per-part CAS, optimistic no-lock, whole-method lock — each with a concrete reason) |
| 25 | Developer can implement without inventing an identifier/limit/shape | PASS (§11 implementation notes; seed values given) |
| 26 | Tester can write an assertion for every AC from the design alone | PASS with F-2.r1.1 (MINOR) — equality-assertion method for `requiredParts` should be pinned in the plan |
