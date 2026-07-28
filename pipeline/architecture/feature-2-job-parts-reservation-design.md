# Feature 2 — Job Parts Reservation — Design

| Field | Value |
|---|---|
| **Revision** | r1 |
| **Feature spec** | `pipeline/features/feature-2-job-parts-reservation.md` |
| **ADR** | `pipeline/decisions/feature-2-job-parts-reservation-adr.md` |
| **Status** | Awaiting review |
| **Author** | architect-agent |
| **Date** | 2026-07-28 |
| **Inherited constraints** | **D-1.1** — the 45-minute `TRAVEL_BUFFER_MINUTES` overlap policy stays untouched; this feature hooks into `bookJob(...)` without altering the overlap/buffer logic. **D-1.2** — `bookJob(...)` is an uncontrolled check-then-act with no booking-level serialization, so this feature guards its own reservation invariant independently (see D-2.1) rather than assuming the booking path serializes. |
| **Decisions raised** | D-2.1, D-2.2, D-2.3 |

## 1. Scope of change

| Layer | File | Change | Traces to |
|---|---|---|---|
| Controller | `backend/src/main/java/com/serviceforge/controller/JobController.java` | Modified — pass `request.getRequiredParts()` to the extended `bookJob(...)`; no new catch clauses (reuses existing 404/409 mapping) | AC-2.1, AC-2.2, AC-2.3, AC-2.6 |
| Service | `backend/src/main/java/com/serviceforge/service/PartsReservationService.java` | New — owns all reads/writes of the parts pool; exposes `reserveForJob(List<RequiredPart>)` which resolves, checks and reserves atomically | AC-2.1, AC-2.2, AC-2.3, AC-2.5 |
| Service | `backend/src/main/java/com/serviceforge/service/TechnicianAvailabilityService.java` | Modified — `bookJob(...)` gains a `List<RequiredPart> requiredParts` parameter; after the existing technician-resolve and overlap checks pass, it calls `PartsReservationService.reserveForJob(...)`, then saves the `Job` carrying `requiredParts` | AC-2.1, AC-2.4 |
| DTO | `backend/src/main/java/com/serviceforge/dto/RequiredPartRequest.java` | New — `{ @NotNull Long partId; @NotNull @Positive Integer quantity; }` with getters/setters | AC-2.6 |
| DTO | `backend/src/main/java/com/serviceforge/dto/BookJobRequest.java` | Modified — add `@Valid private List<RequiredPartRequest> requiredParts;` (nullable/absent allowed) with getter/setter | AC-2.1, AC-2.4, AC-2.6 |
| Model | `backend/src/main/java/com/serviceforge/model/Part.java` | New — `Long id; String name; int quantityOnHand; int quantityReserved;` (`quantityReserved` mutable) | AC-2.1, AC-2.2 |
| Model | `backend/src/main/java/com/serviceforge/model/RequiredPart.java` | New — immutable value: `Long partId; int quantity;` — the shape carried on `Job.requiredParts` and passed into the service | AC-2.1 |
| Model | `backend/src/main/java/com/serviceforge/model/Job.java` | Modified — add `private final List<RequiredPart> requiredParts;`; retain the existing 6-arg constructor delegating to an empty list; add a 7-arg constructor accepting `requiredParts` | AC-2.1, AC-2.4 |
| Store | `backend/src/main/java/com/serviceforge/data/MockDataStore.java` | Modified — add `private final List<Part> parts`; seed parts in `seed()`; add `getAllParts()` and `findPart(Long)` read accessors | AC-2.1, AC-2.3, AC-2.5 |
| Frontend | `frontend/src/app/models/job.model.ts` | Modified — add `requiredParts?: { partId: number; quantity: number }[]` to both `Job` and `BookJobRequest` interfaces so the field round-trips | AC-2.1, AC-2.4 |

**Explicitly not touched:**
- `TechnicianController.java`, `technician.service.ts`, `technician-calendar.component.*` — no parts CRUD UI is in scope (spec Out of scope); the frontend model change is only so the existing booking payload can carry `requiredParts` if a caller supplies it.
- The `TRAVEL_BUFFER_MINUTES` constant and the interval-overlap logic in `bookJob(...)` — Feature 1's travel-buffer decision binds this feature; the parts check is added alongside, never in place of, that logic.
- `JobStatus.java`, `Technician.java` — unrelated to parts.
- No new HTTP endpoint for parts. Part state is asserted by the tester through the injected `MockDataStore` bean (see §7), not via a read API — adding a `GET /api/parts` endpoint would be scope creep (no AC requires it).

## 2. State model

### 2.1 Store delta

| Structure in `MockDataStore` | Type | Key | Value shape | New or existing |
|---|---|---|---|---|
| `parts` | `List<Part>` | linear scan by `id` via `findPart(Long)` | `Part{ id, name, quantityOnHand, quantityReserved }` | New — mirrors the existing `List<Technician>` seeding/access pattern |
| `jobs` | `List<Job>` | linear scan by `technicianId` | `Job{ …, requiredParts: List<RequiredPart> }` | Existing — value shape extended by `D-2.3` |

`Part` uses the same plain `List` + linear-scan access as `technicians`, deliberately, to stay consistent with the store's established shape (migration-safety: no consumer of `technicians`/`jobs` sees a changed collection type). Mutation of a `Part`'s `quantityReserved` happens **only** inside `PartsReservationService.reserveForJob(...)`, inside the `synchronized` block (`D-2.1`).

### 2.2 Invariants

The mock store enforces nothing. Each invariant below is upheld by application code at the named point.

| Invariant | Upheld where | Violated if |
|---|---|---|
| For every `Part`, `quantityReserved <= quantityOnHand` at all times | `PartsReservationService.reserveForJob(...)`, inside the `synchronized` block, before any increment | The check and the increment are not in the same critical section (check-then-act race — S-2.5) |
| A referenced `partId` matches a seeded `Part` before any reservation | `PartsReservationService.reserveForJob(...)`, `findPart(partId)` resolution step | An unknown `partId` is silently skipped instead of rejected (S-2.3) |
| Reservation is all-or-nothing across every requested part | `PartsReservationService.reserveForJob(...)` — every part is resolved and availability-checked *before* the first increment | Any part is incremented before all parts are known-available (partial reservation on S-2.2/S-2.3) |
| A requested `quantity` is a positive integer | `RequiredPartRequest` bean validation (`@NotNull @Positive`), applied by `@Valid` in the controller, before the service is called | Validation is placed on the service instead of the DTO, changing the error path off the 400 route (S-2.6) |
| Duplicate `partId` entries in one request are summed to one requirement before the availability check | `PartsReservationService.reserveForJob(...)`, normalization step (per assumption A-2.2) | Duplicate entries are checked/reserved independently, under- or double-counting the requirement |

### 2.3 Lifecycle

- **Seeded with:** in `MockDataStore.seed()`, alongside the existing technicians/jobs — a small fixed set of parts with `quantityReserved = 0`, e.g. `Part(1L, "HVAC Compressor", 5, 0)`, `Part(2L, "Thermostat", 10, 0)`, `Part(3L, "Air Filter", 2, 0)`. The low-stock part (`id 3`, on-hand 2) exists specifically so a concurrency test (S-2.5) can issue two requests each needing 2 and observe exactly one succeed.
- **On restart:** all `parts` (including every accumulated `quantityReserved`) reset to the seeded values, exactly as `technicians` and `jobs` do. No acceptance criterion assumes durability across restart — every scenario is expressed relative to the seeded starting state, so this is correct, not a gap.

## 3. Interfaces

### `POST /api/jobs`

- **Purpose:** schedule a job for a technician and, atomically, reserve every part it requires.
- **Request DTO:** `BookJobRequest` — existing fields (`@NotNull Long technicianId`, `@NotBlank String customerName`, `@NotNull LocalDateTime startTime`, `@NotNull LocalDateTime endTime`) plus new `@Valid List<RequiredPartRequest> requiredParts` (absent or empty allowed — A-2.1). Each `RequiredPartRequest` = `@NotNull Long partId`, `@NotNull @Positive Integer quantity`. Validation applied by `@Valid @RequestBody` in `JobController`; the field-level `@Valid` cascades into each list element.
- **Response 2xx:** `201 Created`, body is the created `Job` including `requiredParts` (the submitted list, or empty when none was supplied).
- **Errors:**
  - `400` → any bean-validation failure, including a `requiredParts` entry with missing `partId` or a non-positive `quantity` → `ApiError` body → not retryable without fixing the payload.
  - `404` → `technicianId` matches no technician (Feature 1), **or** any `partId` matches no seeded `Part` → `ApiError` naming the unknown id → not retryable as-is.
  - `409` → requested time overlaps an existing job (Feature 1), **or** one or more parts are short → `ApiError` naming every short part with requested and available quantities → retryable once stock frees or the request shrinks.
- **Idempotency:** none. Each `POST` is independent; replaying the same request reserves the parts again (A-2.6 — no idempotency mechanism exists in Feature 1, and this feature adds none).
- **Called by:** `TechnicianService.bookJob(...)` in the Angular `technician.service.ts` (unchanged call site; payload may now include `requiredParts`).

### `PartsReservationService.reserveForJob(List<RequiredPart> required)` — New

- **Purpose:** resolve, availability-check and reserve every requested part atomically; the single owner of `quantityReserved` mutation.
- **Signature:** `void reserveForJob(List<RequiredPart> required)`; returns normally on success (every `Part.quantityReserved` incremented by its summed requested quantity), or throws before any mutation.
- **Errors:** `IllegalArgumentException` when a `partId` is unknown (→ controller 404); `IllegalStateException` when any part is short, its message listing each short part's `partId`, requested and available quantities (→ controller 409).
- **Concurrency:** the entire body — normalize duplicates → resolve each `partId` → compute `quantityOnHand - quantityReserved` for each → if any short, throw → else increment each `quantityReserved` — runs inside one `synchronized` block (`D-2.1`).
- **Called by:** `TechnicianAvailabilityService.bookJob(...)`, after the technician-resolve and overlap checks pass and before the `Job` is saved.

### `TechnicianAvailabilityService.bookJob(...)` — Modified

- **Signature:** `Job bookJob(Long technicianId, String customerName, LocalDateTime startTime, LocalDateTime endTime, List<RequiredPart> requiredParts)`.
- **Behaviour:** unchanged Feature 1 checks (technician exists → 404; interval overlap incl. travel buffer → 409), then `partsReservationService.reserveForJob(requiredParts)`, then `dataStore.save(job)` with the `Job` carrying `requiredParts`. An empty/null `requiredParts` skips reservation entirely (A-2.1).

## 4. Control flow

### 4.1 Happy path (covers S-2.1)

1. `JobController.bookJob` — `@Valid` validates `BookJobRequest` and each `RequiredPartRequest` (400 on failure).
2. Controller maps each `RequiredPartRequest` → `RequiredPart` and calls `availabilityService.bookJob(..., requiredParts)`.
3. `TechnicianAvailabilityService.bookJob` — `dataStore.findTechnician(id)`; absent → `IllegalArgumentException` (404).
4. Same method — interval-overlap check against existing jobs (Feature 1, travel buffer preserved); conflict → `IllegalStateException` (409).
5. `PartsReservationService.reserveForJob(requiredParts)` — **inside `synchronized`:** normalize duplicate `partId`s; resolve each via `dataStore.findPart` (unknown → `IllegalArgumentException`, 404); compute availability per part; collect all short parts and, if non-empty, throw `IllegalStateException` (409); otherwise increment each resolved `Part.quantityReserved` by its summed requested quantity.
6. `TechnicianAvailabilityService` — construct `Job` with `requiredParts` and `dataStore.save(job)`.
7. Controller returns `201` with the `Job`.

**Atomicity boundary:** step 5 in its entirety must be atomic with respect to concurrent callers — the availability read and the `quantityReserved` write cannot be split. Mechanism: a single `synchronized` block on a dedicated `private final Object` monitor owned by `PartsReservationService` (`D-2.1`). Steps 3–4 (Feature 1) and step 6 (`save`) are outside that boundary; because reservation (step 5) is the last checked mutation and the `Job` append cannot fail, no partial state or reservation leak arises.

### 4.2 Sequence

```
TechnicianCalendarComponent -> TechnicianService: bookJob(request incl. requiredParts)
TechnicianService -> JobController: POST /api/jobs
JobController -> TechnicianAvailabilityService: bookJob(..., requiredParts)
TechnicianAvailabilityService -> MockDataStore: findTechnician / getJobsForTechnician (read)
TechnicianAvailabilityService -> PartsReservationService: reserveForJob(requiredParts)
PartsReservationService -> MockDataStore: findPart (read, inside synchronized)
PartsReservationService -> MockDataStore: Part.quantityReserved += qty (write, inside synchronized)
TechnicianAvailabilityService -> MockDataStore: save(job with requiredParts) (write)
JobController -> TechnicianService: 201 Job
```

## 5. Failure design

| Scenario | Failure mode | Detection | Behaviour | Surfaced to caller as | Recovery owner |
|---|---|---|---|---|---|
| S-2.2 | One+ parts short (`requested > available`) | `reserveForJob`, availability compare inside `synchronized`, collects **all** short parts | Reject — throw before any increment; no `Job` saved | `409` + `ApiError` message naming every short `partId` with requested and available quantities | Caller (retry with less/after stock frees) |
| S-2.3 | `partId` matches no seeded `Part` | `reserveForJob`, `dataStore.findPart` returns empty | Reject — throw before any increment; no `Job` saved | `404` + `ApiError` naming the unknown `partId` | Caller (fix the id) |
| S-2.4 | Empty/absent `requiredParts` | `bookJob` — null/empty list guard | Skip reservation; proceed to save | `201` + `Job` with empty `requiredParts` | N/A (success) |
| S-2.5 | Two concurrent requests exhaust one shared `Part` | `reserveForJob` `synchronized` serializes the two check-then-reserve sequences | First reserves; second re-reads the now-lower availability and is short | Winner `201`; loser `409` (as S-2.2); `quantityReserved` never exceeds `quantityOnHand` | Caller (loser retries) |
| S-2.6 | Entry missing `partId` or non-positive `quantity` | `@Valid` bean validation on `RequiredPartRequest`, before the service runs | Reject — service never invoked; no `Job` saved | `400` + `ApiError` | Caller (fix the payload) |

## 6. Concurrency

Every read-then-write against `MockDataStore` gets a row.

| Shared state | Racing operations | Control | Consequence if control is omitted |
|---|---|---|---|
| `Part.quantityReserved` (parts pool) | Two `reserveForJob` calls each read `quantityOnHand - quantityReserved` then increment `quantityReserved` | Single `synchronized` block covering resolve + check + increment for the whole request (`D-2.1`) | Both read the same availability and both reserve → combined `quantityReserved` exceeds `quantityOnHand` → S-2.5 fails |
| `Part.quantityReserved` across multiple parts in one request | Multi-part all-or-nothing reserve | Same block resolves and checks **all** parts before incrementing **any** | A later part being short after earlier parts were already incremented → partial reservation (violates AC-2.2) |
| `jobs` list (overlap check → `save`) | Two concurrent `bookJob` calls for the same technician | **None added by Feature 2** — this is Feature 1's pre-existing check-then-act on `jobs`; out of scope here (spec Out of scope adds no cancellation/booking-concurrency work) | Two overlapping jobs for one technician could both be saved. This does **not** affect any Feature 2 AC: part over-reservation is prevented independently inside `reserveForJob`'s lock, so `quantityReserved` stays correct even if the overlap race fires. |

## 7. Observability

| Signal | Type | Emitted where | Asserted by |
|---|---|---|---|
| `201` + `Job.requiredParts` equal to the submitted list | Response | `JobController` success path | AC-2.1, AC-2.4 |
| Each referenced `Part.quantityReserved` incremented by exactly the requested amount; `quantityOnHand` unchanged | Store state (read via injected `MockDataStore.getAllParts()`/`findPart` in a `@SpringBootTest`) | `PartsReservationService.reserveForJob` | AC-2.1, AC-2.5 |
| No `Part.quantityReserved` changed after a rejected request | Store state (injected `MockDataStore`) | reservation not reached / throws before increment | AC-2.2, AC-2.3, AC-2.6 |
| Job count unchanged after a rejected request | Store state (`MockDataStore.getAllJobs().size()`) | `bookJob` throws before `save` | AC-2.2, AC-2.3, AC-2.6 |
| `409` `ApiError.message` naming every short `partId` with requested and available quantities | Response | `reserveForJob` `IllegalStateException` message | AC-2.2 |
| `404` `ApiError.message` naming the unknown `partId` | Response | `reserveForJob` `IllegalArgumentException` message | AC-2.3 |
| `400` `ApiError` on malformed `requiredParts` entry | Response | `@Valid` → Spring validation handling | AC-2.6 |

## 8. Non-functional envelope

| Dimension | Target | Basis | Enforced by |
|---|---|---|---|
| Concurrency safety | Combined reservations for any `Part` never exceed `quantityOnHand` under concurrent `POST /api/jobs` | AC-2.5 | `synchronized` block in `PartsReservationService.reserveForJob` (`D-2.1`) |
| Input validation | Malformed `requiredParts` entry → 400 before any state read | AC-2.6 | Bean Validation (`@NotNull`, `@Positive`, `@Valid`) on `RequiredPartRequest`/`BookJobRequest` |
| Authorisation | None — the Dispatcher is a named persona only; the API stays unauthenticated exactly as Feature 1 | Spec In/Out of scope, A-2.5 | N/A — no auth model introduced; any caller may `POST /api/jobs` |
| Frontend state refresh | The existing calendar view re-fetches the technician's jobs after a successful booking (unchanged Feature 1 behaviour); no parts view exists | Spec Out of scope (no parts UI) | `technician-calendar.component` existing refresh; no change |

## 9. Binding decisions — index

Pointer only. Every citable decision is authored in the ADR under `pipeline/decisions/`.

| ID | Title | Authored in |
|---|---|---|
| D-2.1 | Multi-part stock check and reserve run inside one `synchronized` block | `pipeline/decisions/feature-2-job-parts-reservation-adr.md` |
| D-2.2 | Parts error mapping reuses Feature 1's convention: unknown → 404, insufficient → 409, malformed → 400 | `pipeline/decisions/feature-2-job-parts-reservation-adr.md` |
| D-2.3 | `Job` and `BookJobRequest` gain `requiredParts` as a backward-compatible, empty-by-default extension | `pipeline/decisions/feature-2-job-parts-reservation-adr.md` |

## 10. Traceability

| AC | Design element | Verified by |
|---|---|---|
| AC-2.1 | §4.1 steps 1–7, §3 `POST /api/jobs` + `reserveForJob`, §2.1 `parts`, §7 rows 1–2 | Integration test (201, `Job.requiredParts`, `quantityReserved` deltas via injected store) |
| AC-2.2 | §5 S-2.2, §6 rows 1–2, §7 `409` + store-unchanged rows | Integration test (409, message names short parts, no job, no reservation change) |
| AC-2.3 | §5 S-2.3, §7 `404` + store-unchanged rows | Integration test (404, message names unknown `partId`, no job, no reservation change) |
| AC-2.4 | §4.1 step 5 guard, §3 empty-list behaviour, §7 row 1 | Integration test (201, empty `requiredParts`, no reservation change) |
| AC-2.5 | §4.1 step 5 atomicity boundary, §6 row 1, §2.3 low-stock seed | Concurrency integration test (two concurrent requests, one 201 one 409, `quantityReserved <= quantityOnHand`) |
| AC-2.6 | §3 validation, §5 S-2.6, §7 `400` row | Integration test (400, no job, no reservation change) |

## 11. Implementation notes for the developer agent

- Hold the reservation atomicity in exactly one place: the `synchronized` block inside `PartsReservationService.reserveForJob`. Resolve **and** availability-check **every** part before incrementing **any** — collect all short parts first so the 409 message can name them all in one response (AC-2.2 requires "every short part").
- Sum duplicate `partId` entries within a single request into one requirement before the availability check (A-2.2), so a request asking for the same part twice is not under- or double-counted.
- Extend `Job` and `BookJobRequest` additively (`D-2.3`): keep the existing 6-arg `Job` constructor (delegating `requiredParts` to `List.of()`) so `MockDataStore.seed()` and any current caller still compile; add a 7-arg constructor for the new path. Reuse `JobController`'s existing `catch (IllegalArgumentException)`→404 / `catch (IllegalStateException)`→409 — do **not** add new catch clauses (`D-2.2`).
- **Do not** put a `synchronized` keyword on the availability read but call the increment outside the lock, and **do not** reserve part-by-part with an early increment then a rollback on a later shortage — either reintroduces the check-then-act race (S-2.5) or a partial-reservation window (S-2.2). One block, all checks before any write.
- **Do not** add a `GET /api/parts` endpoint or any parts CRUD UI to satisfy the tester — part state is asserted through the injected `MockDataStore` bean; a read API is out of scope.
- **Do not** touch `TRAVEL_BUFFER_MINUTES` or the interval-overlap logic; the parts check is added alongside it, not in its place.

## 12. Revision Log

| Revision | Finding addressed | What changed |
|---|---|---|
| r1 | — | Initial design. Reviewed at `pipeline/reviews/review-2-r1.md` (APPROVED, 0 blockers, 0 majors, 2 minors — both carried into the plan, see F-2.r1.1 / F-2.r1.2). |
