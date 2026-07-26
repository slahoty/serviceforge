# Feature 2 — Job Parts Reservation

| Field | Value |
|---|---|
| **Status** | Candidate-built / in progress |
| **One-line intent** | When a job is scheduled, check that its required parts are in stock and reserve them for that job. |
| **Depends on** | Feature 1, decision "standard travel buffer per job is 45 minutes": this decision explicitly anticipates parts-reservations tied to booked jobs, and establishes that job creation happens via `POST /api/jobs` → `TechnicianAvailabilityService.bookJob(...)`, which is the trigger point this feature hooks into to check and reserve stock before a job is persisted. |
| **In scope** | Introducing a `Dispatcher` role in this spec as the actor who schedules jobs (no auth/user model — the API stays unauthenticated exactly as in Feature 1); extending job-scheduling requests to accept a list of required parts as `{partId, quantity}` pairs; extending the `Job` record with a `requiredParts` field recording that list; introducing a `Part` entity (`id`, `name`, `quantityOnHand`, `quantityReserved`) seeded at startup via `MockDataStore`, matching the existing technician-seeding pattern; checking availability (`quantityOnHand - quantityReserved`) for every requested part against the single central stock pool before the job is created; reserving stock (incrementing `quantityReserved`) for every requested part, all at once, only when every requested part is available; rejecting the entire booking — no job created, no partial reservation — when any requested part is unavailable, unknown, or malformed; an error response on rejection that names every short part with its requested and available quantities. |
| **Out of scope** | Releasing/un-reserving a part's `quantityReserved` when a job is cancelled — no cancellation flow exists yet; restocking or receiving new inventory (increasing `quantityOnHand`) by any means other than startup seeding; deducting/consuming stock (reducing `quantityOnHand`) when a job is completed; any parts CRUD UI (create, edit, delete parts) in the frontend; any authentication/authorization or user-account model for the Dispatcher role — it exists in this spec as a named persona only, not as a login-backed identity; per-technician or per-van stock pools — this feature uses a single central pool only; any job-type-to-parts catalog/mapping — required parts are supplied explicitly by the Dispatcher on each request, never inferred. |
| **Definition of Done** | Job-scheduling requests can include a list of required parts (`partId`, `quantity`) — AC-2.1, AC-2.4. Stock availability (`quantityOnHand - quantityReserved`) is checked for every requested part before the job is created — AC-2.1, AC-2.2, AC-2.3. When all requested parts are available, each part's `quantityReserved` is incremented and the job is created with its `requiredParts` recorded — AC-2.1. When any requested part is unavailable, unknown, or malformed, the entire booking is rejected, no job is created, and no part's `quantityReserved` changes — AC-2.2, AC-2.3, AC-2.6. Concurrent scheduling requests never cause a part's combined reservations to exceed its `quantityOnHand` — AC-2.5. |

## User Story

**US-2.1**

**As a** Dispatcher,
**I want to** have a job's required parts automatically checked against stock and reserved when I schedule the job,
**so that** a job is never scheduled unless the parts it needs are actually available, avoiding a technician being sent to a job it can't complete.

**INVEST check:** Independent y · Negotiable y · Valuable y · Estimable y · Small y · Testable y

### Scenarios

#### Happy path — S-2.1

**Given** a Dispatcher is scheduling a job for a technician, and every part in the job's requested `{partId, quantity}` list has an available quantity (`quantityOnHand - quantityReserved`) greater than or equal to the requested quantity
**When** the Dispatcher submits the job-scheduling request with that `requiredParts` list
**Then** the request returns 201 with the created `Job`, whose `requiredParts` field matches the submitted list, and each referenced `Part`'s `quantityReserved` has increased by exactly its requested quantity while `quantityOnHand` is unchanged

#### Insufficient stock — S-2.2

**Given** a Dispatcher is scheduling a job whose `requiredParts` list includes at least one part whose requested quantity exceeds its available quantity (`quantityOnHand - quantityReserved`)
**When** the Dispatcher submits the job-scheduling request
**Then** the request returns 409 with an `ApiError` body whose message names every short part by `partId`, its requested quantity, and its available quantity; no `Job` is created (the job count is unchanged); and no `Part` referenced in the request has its `quantityReserved` changed, including parts that were individually available

#### Unknown part reference — S-2.3

**Given** a Dispatcher is scheduling a job whose `requiredParts` list includes a `partId` that does not match any seeded `Part`
**When** the Dispatcher submits the job-scheduling request
**Then** the request returns 404 with an `ApiError` body naming the unknown `partId`, mirroring the existing 404 convention `TechnicianAvailabilityService` already uses for an unknown `technicianId`; no `Job` is created; and no `Part`'s `quantityReserved` changes

#### Empty required-parts list — S-2.4

**Given** a Dispatcher is scheduling a job and supplies an empty or absent `requiredParts` list
**When** the Dispatcher submits the job-scheduling request
**Then** the request returns 201, the created `Job`'s `requiredParts` field is empty, and no `Part`'s `quantityReserved` changes

#### Concurrent scheduling exhausts shared stock — S-2.5

**Given** a single `Part` whose available quantity (`quantityOnHand - quantityReserved`) is enough for either one of two pending job-scheduling requests individually, but not enough for both combined
**When** the two requests are submitted concurrently, each requiring that part
**Then** at most one request returns 201 and reserves the part (incrementing its `quantityReserved` by its requested quantity), the other returns 409 as insufficient stock with no `Job` created for it, and the part's final `quantityReserved` never exceeds its `quantityOnHand`

#### Malformed part entry — S-2.6

**Given** a Dispatcher is scheduling a job whose `requiredParts` list includes an entry with a missing `partId` or a quantity that is not a positive integer
**When** the Dispatcher submits the job-scheduling request
**Then** the request returns 400 with an `ApiError` body, mirroring the existing `@NotNull`/`@NotBlank` validation pattern already used on `BookJobRequest`; no `Job` is created; and no `Part`'s `quantityReserved` changes

*(Permission boundary and downstream-unavailable categories are ruled inapplicable — see A-2.5. Duplicate submission of an identical request is ruled inapplicable — see A-2.6.)*

### Acceptance Criteria

- [ ] **AC-2.1** — A job-scheduling request whose every requested part has available quantity ≥ requested quantity returns 201, creates a `Job` whose `requiredParts` matches the request, and increments `quantityReserved` by exactly the requested amount for each referenced `Part` without changing `quantityOnHand` *(covers S-2.1)*
- [ ] **AC-2.2** — A job-scheduling request with at least one part whose requested quantity exceeds its available quantity returns 409 with an `ApiError` naming every short `partId` with requested and available quantities, creates no `Job`, and changes no `Part`'s `quantityReserved` *(covers S-2.2)*
- [ ] **AC-2.3** — A job-scheduling request referencing a `partId` that matches no seeded `Part` returns 404 with an `ApiError` naming the unknown `partId`, creates no `Job`, and changes no `Part`'s `quantityReserved` *(covers S-2.3)*
- [ ] **AC-2.4** — A job-scheduling request with an empty or absent `requiredParts` list returns 201, creates a `Job` with an empty `requiredParts` field, and changes no `Part`'s `quantityReserved` *(covers S-2.4)*
- [ ] **AC-2.5** — For two concurrent job-scheduling requests each requiring the same `Part` in a quantity that is individually satisfiable but not satisfiable in combination, exactly one request succeeds with 201 and a reservation, the other is rejected with 409, and the `Part`'s final `quantityReserved` never exceeds its `quantityOnHand` *(covers S-2.5)*
- [ ] **AC-2.6** — A job-scheduling request with a `requiredParts` entry missing `partId` or having a non-positive quantity returns 400, creates no `Job`, and changes no `Part`'s `quantityReserved` *(covers S-2.6)*

## Assumptions

| ID | Assumption | Basis | Impact if wrong |
|---|---|---|---|
| A-2.1 | A job may be scheduled with an empty or absent `requiredParts` list, in which case no stock check or reservation occurs and the job is created exactly as in Feature 1. | Human confirmed required parts are supplied explicitly by the Dispatcher on the request; no minimum-length rule was given, so this reduces to Feature 1's existing unrestricted behavior. | Developer would need to make `requiredParts` mandatory/non-empty, requiring a spec update. |
| A-2.2 | Duplicate `partId` entries within a single request's `requiredParts` list are summed to one combined-quantity requirement before the availability check. | Immaterial data-shape detail not among the six completeness items; consistent with the all-or-nothing reservation rule the human confirmed. | A request with duplicate part entries could double-count or under-count the required quantity. |
| A-2.3 | An unknown `partId` (matching no seeded `Part`) is rejected with the same 404 + `ApiError` convention `TechnicianAvailabilityService` already uses for an unknown `technicianId`, distinct from the 409 used for insufficient-but-known parts. | Reuse of the established error-handling convention already present in `JobController`/`TechnicianAvailabilityService` (`IllegalArgumentException` → 404, `IllegalStateException` → 409). | Unknown-part rejections would need to move to the 409 path instead, changing AC-2.3's status code. |
| A-2.4 | Malformed `requiredParts` entries (missing `partId` or non-positive quantity) are rejected with a 400 validation error, mirroring the existing `@NotNull`/`@NotBlank` field-validation pattern already used on `BookJobRequest`. | Reuse of the established Bean Validation convention already in `BookJobRequest`. | Malformed entries would need a different status code or handling path. |
| A-2.5 | The "permission boundary" and "downstream unavailable" edge-case categories are inapplicable: the Dispatcher role carries no authentication/authorization model (per human confirmation), and the `Part` store is in-memory with no external downstream dependency, matching the existing `MockDataStore` pattern. | Human confirmed no auth/user model for this feature; `AGENTS.md` establishes mock/in-memory data only, no external services, repo-wide. | N/A — would only need revisiting if auth or a real database were introduced, which is out of scope repo-wide. |
| A-2.6 | "Duplicate submission" (the identical booking request sent twice, e.g. a double click) is inapplicable as a distinct edge case here because Feature 1's booking flow has no idempotency/dedup mechanism today; each submission is treated as independent, and this feature does not change that boundary. | Feature 1's spec and code (`TechnicianAvailabilityService.bookJob`, `JobController`) have no idempotency handling for `POST /api/jobs`. | Duplicate submissions could double-reserve parts; would require an idempotency-key mechanism as a future feature. |

## Traceability

| DoD item | Satisfied by |
|---|---|
| Job-scheduling requests can include a list of required parts (`partId`, `quantity`) | AC-2.1, AC-2.4 |
| Stock availability (`quantityOnHand - quantityReserved`) is checked for every requested part before the job is created | AC-2.1, AC-2.2, AC-2.3 |
| When all requested parts are available, each part's `quantityReserved` is incremented and the job is created with its `requiredParts` recorded | AC-2.1 |
| When any requested part is unavailable, unknown, or malformed, the entire booking is rejected, no job is created, and no part's `quantityReserved` changes | AC-2.2, AC-2.3, AC-2.6 |
| Concurrent scheduling requests never cause a part's combined reservations to exceed its `quantityOnHand` | AC-2.5 |

## Artifacts this feature touches
< filled in as the developer and tester agents do their work > 
