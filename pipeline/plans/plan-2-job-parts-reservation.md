# Implementation Plan — Feature 2: Job Parts Reservation

| Field | Value |
|---|---|
| **Spec** | `pipeline/features/feature-2-job-parts-reservation.md` |
| **Design** | `pipeline/architecture/feature-2-job-parts-reservation-design.md` (revision r1) |
| **Approving review** | `pipeline/reviews/review-2-r1.md` — APPROVED |
| **Decisions binding this plan** | D-2.1: the entire resolve → check-all → reserve-all sequence runs inside one `synchronized` block owned by `PartsReservationService`; D-2.2: unknown `partId` → 404 (`IllegalArgumentException`), insufficient stock → 409 (`IllegalStateException`), malformed entry → 400 (Bean Validation), reusing Feature 1's mapping; D-2.3: `Job` and `BookJobRequest` gain `requiredParts` as an additive, empty-by-default field (keep the existing 6-arg `Job` constructor). |
| **Rules applied** | none exist yet under `pipeline/rules/` |
| **Task count** | 16 across 6 waves |

## Execution order

| Wave | Tasks | Gate to leave this wave |
|---|---|---|
| 1 | T-2.1, T-2.2, T-2.5 | All three compile; leaf models/DTO exist |
| 2 | T-2.3, T-2.4, T-2.6 | Backend compiles with extended `Job`, seeded `parts`, extended `BookJobRequest` |
| 3 | T-2.7, T-2.10 | `PartsReservationService` compiles; frontend model carries `requiredParts` |
| 4 | T-2.8 | `bookJob(...)` compiles and calls `reserveForJob` after the overlap check |
| 5 | T-2.9 | `mvn -q compile` succeeds; `POST /api/jobs` wired to the parts flow |
| 6 | T-2.11, T-2.12, T-2.13, T-2.14, T-2.15, T-2.16 | `mvn test` green; every AC has a passing test |

## Tasks

### T-2.1 — Add the `Part` model

| Field | Value |
|---|---|
| **Wave** | 1 |
| **Agent** | developer |
| **Depends on** | — |
| **Implements** | §1 Model `Part`; §2.1 store value shape |
| **Advances** | AC-2.1, AC-2.2 |
| **Files** | `backend/src/main/java/com/serviceforge/model/Part.java` |

**Do:** Create a plain data class `Part` with `Long id`, `String name`, `int quantityOnHand`, `int quantityReserved`. `id`, `name`, `quantityOnHand` are set once via constructor; `quantityReserved` is mutable (getter + setter) and starts at whatever the constructor is given. Add a convenience for available = `quantityOnHand - quantityReserved` only if trivially useful; no business logic in the model.

**Constrained by:** D-2.1 — only `PartsReservationService` mutates `quantityReserved`; the model just exposes the setter, it does not guard concurrency itself.

**Completion check:** `Part.java` compiles; `new Part(3L, "Air Filter", 2, 0).getQuantityOnHand()` returns 2 and `getQuantityReserved()` returns 0.

**Out of scope for this task:** seeding parts (T-2.4); reservation logic (T-2.7).

---

### T-2.2 — Add the `RequiredPart` model (immutable value)

| Field | Value |
|---|---|
| **Wave** | 1 |
| **Agent** | developer |
| **Depends on** | — |
| **Implements** | §1 Model `RequiredPart` |
| **Advances** | AC-2.1 |
| **Files** | `backend/src/main/java/com/serviceforge/model/RequiredPart.java` |

**Do:** Create an immutable value class `RequiredPart` with `Long partId` and `int quantity`, both final, constructor-set, getters only. Implement `equals`/`hashCode` on `partId` + `quantity` so a stored `Job.requiredParts` can be asserted by value (this pins review finding F-2.r1.1).

**Constrained by:** D-2.3 — this is the shape carried on `Job.requiredParts`; it must be safe to store an empty `List<RequiredPart>` for jobs booked without parts.

**Completion check:** `RequiredPart.java` compiles; `new RequiredPart(1L, 2).equals(new RequiredPart(1L, 2))` is `true`.

**Out of scope for this task:** the request DTO `RequiredPartRequest` (T-2.5); summing duplicates (T-2.7).

---

### T-2.5 — Add the `RequiredPartRequest` DTO with validation

| Field | Value |
|---|---|
| **Wave** | 1 |
| **Agent** | developer |
| **Depends on** | — |
| **Implements** | §1 DTO `RequiredPartRequest`; §8 input validation |
| **Advances** | AC-2.6 |
| **Files** | `backend/src/main/java/com/serviceforge/dto/RequiredPartRequest.java` |

**Do:** Create `RequiredPartRequest` with `@NotNull Long partId` and `@NotNull @Positive Integer quantity`, with getters and setters (mirroring `BookJobRequest`'s bean-style shape).

**Constrained by:** D-2.2 — malformed entry (missing `partId` or non-positive `quantity`) must fail Bean Validation so it surfaces as 400, not as a service exception. Use `Integer` (not `int`) so a missing quantity is caught by `@NotNull` rather than defaulting to 0.

**Completion check:** `RequiredPartRequest.java` compiles; the field annotations are `@NotNull` on `partId` and `@NotNull @Positive` on `quantity`.

**Out of scope for this task:** wiring it into `BookJobRequest` (T-2.6).

---

### T-2.3 — Extend `Job` with `requiredParts` (additive)

| Field | Value |
|---|---|
| **Wave** | 2 |
| **Agent** | developer |
| **Depends on** | T-2.2 |
| **Implements** | §1 Model `Job`; D-2.3 |
| **Advances** | AC-2.1, AC-2.4 |
| **Files** | `backend/src/main/java/com/serviceforge/model/Job.java` |

**Do:** Add `private final List<RequiredPart> requiredParts;`. Keep the existing 6-arg constructor, delegating `requiredParts` to `List.of()` (empty, never null). Add a 7-arg constructor accepting `List<RequiredPart> requiredParts` (defensively copy to an unmodifiable list; treat null as empty). Add `getRequiredParts()`.

**Constrained by:** D-2.3 — the 6-arg constructor must remain so `MockDataStore.seed()` and Feature 1's path compile unchanged; `getRequiredParts()` never returns null.

**Completion check:** existing `new Job(id, techId, name, start, end, SCHEDULED)` still compiles and `getRequiredParts()` returns an empty (non-null) list; the 7-arg constructor stores the passed list.

**Out of scope for this task:** the service passing `requiredParts` (T-2.8); the frontend model (T-2.10).

---

### T-2.4 — Extend `MockDataStore` with a seeded parts pool

| Field | Value |
|---|---|
| **Wave** | 2 |
| **Agent** | developer |
| **Depends on** | T-2.1 |
| **Implements** | §1 Store; §2.1 store delta; §2.3 lifecycle/seed |
| **Advances** | AC-2.1, AC-2.3, AC-2.5 |
| **Files** | `backend/src/main/java/com/serviceforge/data/MockDataStore.java` |

**Do:** Add `private final List<Part> parts = new ArrayList<>();`. In `seed()`, add `Part(1L, "HVAC Compressor", 5, 0)`, `Part(2L, "Thermostat", 10, 0)`, `Part(3L, "Air Filter", 2, 0)` — the low-stock part id 3 (on-hand 2) exists for the S-2.5 concurrency test. Add read accessors `List<Part> getAllParts()` (unmodifiable) and `Optional<Part> findPart(Long id)` (linear scan by id), mirroring `getAllTechnicians()`/`findTechnician(...)`.

**Constrained by:** migration-safety-skill — this is a new collection added alongside `technicians`/`jobs`; no existing collection type changes, so no existing consumer of `technicians`/`jobs` breaks. Seed populates every field of every `Part` (`quantityReserved = 0`).

**Completion check:** after `seed()`, `getAllParts()` has 3 parts; `findPart(3L)` is present with `quantityOnHand == 2`, `quantityReserved == 0`; `findPart(999L)` is empty.

**Out of scope for this task:** mutating `quantityReserved` (T-2.7).

---

### T-2.6 — Extend `BookJobRequest` with `requiredParts`

| Field | Value |
|---|---|
| **Wave** | 2 |
| **Agent** | developer |
| **Depends on** | T-2.5 |
| **Implements** | §1 DTO `BookJobRequest`; §3 request DTO; D-2.3 |
| **Advances** | AC-2.1, AC-2.4, AC-2.6 |
| **Files** | `backend/src/main/java/com/serviceforge/dto/BookJobRequest.java` |

**Do:** Add `@Valid private List<RequiredPartRequest> requiredParts;` with getter/setter. No `@NotNull`/`@NotEmpty` on the list itself — absent or empty is allowed (A-2.1). `@Valid` cascades validation into each element.

**Constrained by:** D-2.3 — the field is optional/empty-by-default; existing callers that omit it must still validate and book exactly as in Feature 1.

**Completion check:** `BookJobRequest.java` compiles; a request JSON without `requiredParts` still binds (field is null/empty) and passes bean validation; the field carries `@Valid`.

**Out of scope for this task:** mapping to `RequiredPart` in the controller (T-2.9).

---

### T-2.7 — Create `PartsReservationService.reserveForJob(...)`

| Field | Value |
|---|---|
| **Wave** | 3 |
| **Agent** | developer |
| **Depends on** | T-2.1, T-2.2, T-2.4 |
| **Implements** | §3 `reserveForJob`; §4.1 step 5; §6 rows 1–2; D-2.1 |
| **Advances** | AC-2.1, AC-2.2, AC-2.3, AC-2.5 |
| **Files** | `backend/src/main/java/com/serviceforge/service/PartsReservationService.java` |

**Do:** New `@Service` with constructor-injected `MockDataStore` and a `private final Object monitor = new Object();`. Implement `void reserveForJob(List<RequiredPart> required)`: if null/empty, return immediately. Otherwise, **inside `synchronized (monitor)`**: (1) normalize — sum quantities of duplicate `partId`s into one requirement (A-2.2); (2) resolve every `partId` via `dataStore.findPart(...)`, and if any is unknown throw `IllegalArgumentException` naming the unknown `partId`; (3) compute `quantityOnHand - quantityReserved` for every resolved part and collect **all** short parts; if any are short, throw `IllegalStateException` whose message lists every short `partId` with its requested and available quantities; (4) only if none short, increment each resolved `Part.quantityReserved` by its summed requested quantity.

**Constrained by:** D-2.1 — resolve, check-all, and reserve-all are one critical section on one monitor; never increment before every part is known available; never split the read from the write. D-2.2 — unknown → `IllegalArgumentException` (404), short → `IllegalStateException` (409).

**Completion check:** unit test at service level: reserving `{partId 3, qty 2}` sets part 3 `quantityReserved` to 2; a second reserve of `{partId 3, qty 1}` throws `IllegalStateException` and leaves `quantityReserved` at 2; reserving `{partId 999, qty 1}` throws `IllegalArgumentException` and changes no part.

**Out of scope for this task:** calling it from `bookJob` (T-2.8); the empty-list skip at the booking level (also handled here by early return).

---

### T-2.10 — Update frontend `job.model.ts` for `requiredParts`

| Field | Value |
|---|---|
| **Wave** | 3 |
| **Agent** | developer |
| **Depends on** | T-2.3 |
| **Implements** | §1 Frontend row |
| **Advances** | AC-2.1, AC-2.4 |
| **Files** | `frontend/src/app/models/job.model.ts` |

**Do:** Add `requiredParts?: { partId: number; quantity: number }[]` to the `Job` interface, and to the booking-request interface if one exists in this file, so the payload round-trips. No component or service logic changes.

**Constrained by:** migration-safety-skill — the frontend `Job` model is a consumer of the backend `Job` shape; the new field is optional so existing views that ignore it are unaffected. Spec Out of scope: no parts UI, no `technician.service.ts`/component changes.

**Completion check:** `job.model.ts` declares `requiredParts` as an optional array of `{ partId: number; quantity: number }`; no other frontend file is modified.

**Out of scope for this task:** rendering parts in the calendar (not in scope for this feature).

---

### T-2.8 — Extend `TechnicianAvailabilityService.bookJob(...)`

| Field | Value |
|---|---|
| **Wave** | 4 |
| **Agent** | developer |
| **Depends on** | T-2.3, T-2.7 |
| **Implements** | §3 `bookJob` modified; §4.1 steps 3–6 |
| **Advances** | AC-2.1, AC-2.4 |
| **Files** | `backend/src/main/java/com/serviceforge/service/TechnicianAvailabilityService.java` |

**Do:** Add a `List<RequiredPart> requiredParts` parameter to `bookJob(...)`. Keep the existing technician-resolve (404) and interval-overlap-with-travel-buffer (409) checks exactly as they are. After those pass, call `partsReservationService.reserveForJob(requiredParts)` (which throws on unknown/short, before any save). Then construct the `Job` via the 7-arg constructor carrying `requiredParts` and `dataStore.save(job)`. Constructor-inject `PartsReservationService`.

**Constrained by:** D-2.1 — reservation happens strictly before `save`, so a thrown reservation error leaves no `Job` and no reservation change. Feature 1 constraint — do **not** touch `TRAVEL_BUFFER_MINUTES` or the overlap logic; the parts check is added after it, not in its place.

**Completion check:** service-level test: `bookJob(tech, cust, start, end, [ {3,2} ])` saves a `Job` whose `getRequiredParts()` equals `[RequiredPart(3,2)]` and sets part 3 `quantityReserved` to 2; `bookJob(..., [])` saves a `Job` with empty `requiredParts` and changes no part.

**Out of scope for this task:** controller mapping (T-2.9).

---

### T-2.9 — Wire `JobController` to the parts flow

| Field | Value |
|---|---|
| **Wave** | 5 |
| **Agent** | developer |
| **Depends on** | T-2.6, T-2.8 |
| **Implements** | §1 Controller; §3 `POST /api/jobs`; §4.1 steps 1–2, 7 |
| **Advances** | AC-2.1, AC-2.2, AC-2.3, AC-2.4, AC-2.5, AC-2.6 |
| **Files** | `backend/src/main/java/com/serviceforge/controller/JobController.java` |

**Do:** Map `request.getRequiredParts()` (list of `RequiredPartRequest`, may be null/empty → map to empty `List<RequiredPart>`) into `List<RequiredPart>` and pass to `availabilityService.bookJob(..., requiredParts)`. Keep `@Valid @RequestBody` so malformed entries fail with 400 before the service runs. Reuse the existing `catch (IllegalArgumentException)` → 404 and `catch (IllegalStateException)` → 409 clauses — do **not** add new catch clauses (D-2.2).

**Constrained by:** D-2.2 — no new catch clauses; the existing exception-to-status mapping already produces 404 (unknown) and 409 (short); Bean Validation produces 400.

**Completion check:** `mvn -q compile` succeeds; a `POST /api/jobs` with a valid non-overlapping slot and `requiredParts: [{partId:3, quantity:2}]` returns 201 with the job; with `[{partId:999,...}]` returns 404; with a short part returns 409; with `{quantity:0}` returns 400.

**Out of scope for this task:** adding any `GET /api/parts` endpoint (explicitly excluded, design §1).

---

### T-2.11 — Test AC-2.1 (happy path: reserve + create)

| Field | Value |
|---|---|
| **Wave** | 6 |
| **Agent** | tester |
| **Depends on** | T-2.8, T-2.9 |
| **Implements** | verifies §10 AC-2.1 row |
| **Advances** | AC-2.1 |
| **Files** | `backend/src/test/java/com/serviceforge/...` (service and/or `@SpringBootTest`) |

**Do:** Given seeded parts, book a job (non-overlapping slot) requiring `{partId 1, qty 1}`. Assert: 201 / returned `Job.getRequiredParts()` equals `[RequiredPart(1,1)]` **by value** (F-2.r1.1: `RequiredPart.equals` from T-2.2, or compare `partId`+`quantity` field-wise); part 1 `quantityReserved` increased by exactly 1; part 1 `quantityOnHand` unchanged; job count increased by 1.

**Constrained by:** assert store state via the injected `MockDataStore` bean, not a read API (design §7).

**Completion check:** the test passes and fails if `quantityReserved` is not incremented by exactly the requested amount.

**Out of scope for this task:** other ACs' tests.

---

### T-2.12 — Test AC-2.2 (insufficient stock → 409, all-or-nothing)

| Field | Value |
|---|---|
| **Wave** | 6 |
| **Agent** | tester |
| **Depends on** | T-2.7, T-2.8, T-2.9 |
| **Implements** | verifies §10 AC-2.2 row |
| **Advances** | AC-2.2 |
| **Files** | `backend/src/test/java/com/serviceforge/...` |

**Do:** Book a job requiring two parts where one is available and one is short (e.g. `{partId 1, qty 1}` + `{partId 3, qty 5}` when part 3 on-hand is 2). Assert: 409 / `ApiError.message` names the short `partId 3` with its requested (5) and available (2) quantities; job count unchanged; **neither** part 1 **nor** part 3 has `quantityReserved` changed (proving all-or-nothing, including the individually-available part).

**Constrained by:** D-2.2 — insufficient → 409.

**Completion check:** the test passes and fails if part 1's `quantityReserved` was incremented despite the overall rejection.

**Out of scope for this task:** unknown-part and malformed cases.

---

### T-2.13 — Test AC-2.3 (unknown part → 404)

| Field | Value |
|---|---|
| **Wave** | 6 |
| **Agent** | tester |
| **Depends on** | T-2.7, T-2.8, T-2.9 |
| **Implements** | verifies §10 AC-2.3 row |
| **Advances** | AC-2.3 |
| **Files** | `backend/src/test/java/com/serviceforge/...` |

**Do:** Book a job requiring `{partId 999, qty 1}` (no such seeded part). Assert: 404 / `ApiError.message` names the unknown `partId 999`; job count unchanged; no part's `quantityReserved` changed.

**Constrained by:** D-2.2 — unknown → 404, mirroring unknown-technician.

**Completion check:** the test passes and fails if the status is 409 instead of 404.

**Out of scope for this task:** insufficient-stock case.

---

### T-2.14 — Test AC-2.4 (empty/absent list → 201, no reservation)

| Field | Value |
|---|---|
| **Wave** | 6 |
| **Agent** | tester |
| **Depends on** | T-2.8, T-2.9 |
| **Implements** | verifies §10 AC-2.4 row |
| **Advances** | AC-2.4 |
| **Files** | `backend/src/test/java/com/serviceforge/...` |

**Do:** Book a job with an absent `requiredParts` (and separately an empty list). Assert: 201 / created `Job.getRequiredParts()` is empty; no part's `quantityReserved` changed; job count increased by 1.

**Constrained by:** A-2.1 — empty/absent reduces to Feature 1 behaviour.

**Completion check:** the test passes and fails if any part's `quantityReserved` changes for a parts-free booking.

**Out of scope for this task:** parts-carrying bookings.

---

### T-2.15 — Test AC-2.5 (concurrent exhaustion of shared stock)

| Field | Value |
|---|---|
| **Wave** | 6 |
| **Agent** | tester |
| **Depends on** | T-2.7, T-2.8 |
| **Implements** | verifies §10 AC-2.5 row; §2.3 low-stock seed |
| **Advances** | AC-2.5 |
| **Files** | `backend/src/test/java/com/serviceforge/...` |

**Do:** Using part 3 (on-hand 2), submit two bookings concurrently (two threads, e.g. via `ExecutorService` + `CountDownLatch`), each requiring `{partId 3, qty 2}`, for non-overlapping slots (so the technician-overlap check does not interfere). Assert: exactly one booking succeeds (201, reserves), the other throws `IllegalStateException` / 409; part 3 final `quantityReserved` equals 2 and never exceeds `quantityOnHand` (2).

**Constrained by:** D-2.1 — the `synchronized` block makes this deterministic; do not weaken the assertion to "at most one" if the seed guarantees exactly one.

**Completion check:** the test passes; it would fail (combined `quantityReserved` = 4 > 2) if the `synchronized` block were removed.

**Out of scope for this task:** Feature 1's separate technician-overlap race (design §6 row 3, out of scope).

---

### T-2.16 — Test AC-2.6 (malformed entry → 400)

| Field | Value |
|---|---|
| **Wave** | 6 |
| **Agent** | tester |
| **Depends on** | T-2.6, T-2.9 |
| **Implements** | verifies §10 AC-2.6 row |
| **Advances** | AC-2.6 |
| **Files** | `backend/src/test/java/com/serviceforge/...` (`@SpringBootTest` + MockMvc, or web-layer test) |

**Do:** Submit `POST /api/jobs` with a `requiredParts` entry that has a non-positive quantity (`quantity: 0`) and, separately, a missing `partId`. Assert: 400 with an `ApiError` body; job count unchanged; no part's `quantityReserved` changed. This exercises the `@NotNull @Positive` validation on `RequiredPartRequest` via `@Valid`.

**Constrained by:** D-2.2 — malformed → 400 via Bean Validation, before the service is reached.

**Completion check:** the test passes and fails if a `quantity: 0` entry reaches the service (would surface as 409/other, not 400).

**Out of scope for this task:** valid-but-short parts (T-2.12).

## Traceability

| AC | Implementation task(s) | Test task |
|---|---|---|
| AC-2.1 | T-2.7, T-2.8, T-2.9 (+ T-2.1, T-2.2, T-2.3, T-2.4) | T-2.11 |
| AC-2.2 | T-2.7, T-2.9 | T-2.12 |
| AC-2.3 | T-2.7, T-2.9 | T-2.13 |
| AC-2.4 | T-2.8, T-2.9 (+ T-2.3, T-2.6) | T-2.14 |
| AC-2.5 | T-2.7, T-2.8 (+ T-2.4) | T-2.15 |
| AC-2.6 | T-2.5, T-2.6, T-2.9 | T-2.16 |

## Carried review findings

| Finding | Severity | Disposition |
|---|---|---|
| F-2.r1.1 | MINOR | Addressed — T-2.2 gives `RequiredPart` value equality; T-2.11 asserts `Job.requiredParts` by value |
| F-2.r1.2 | MINOR | Declined — presentation-only; field types (§1) plus constraints (§2.2) are already sufficient for the developer; no build impact |

## Risks

| Risk | Task most affected | Signal it is materialising | Response |
|---|---|---|---|
| Concurrency test is timing-flaky | T-2.15 | Intermittent pass/fail across runs | Use a `CountDownLatch` to release both threads together and join both; assert on final store state, which is deterministic under the `synchronized` block, rather than on which thread won |
| `int` vs `Integer` on `quantity` lets `quantity:0`/missing slip past validation | T-2.5, T-2.16 | `quantity:0` returns 409/201 instead of 400 | Use `@NotNull @Positive Integer quantity` (boxed) so both missing and non-positive fail Bean Validation at 400 |
| Adding a `bookJob` parameter breaks Feature 1's existing test/call sites | T-2.8 | `mvn compile`/existing tests fail | Existing Feature 1 test calls the 4-arg intent; update call sites to pass an empty list, or provide the empty-list behaviour path — keep Feature 1 assertions intact |
