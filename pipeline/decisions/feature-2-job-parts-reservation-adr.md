# ADRs — Feature 2: Job Parts Reservation

| Field | Value |
|---|---|
| **Feature spec** | `pipeline/features/feature-2-job-parts-reservation.md` |
| **Design** | `pipeline/architecture/feature-2-job-parts-reservation-design.md` |
| **Revision** | r1 |

One file, three decisions. Each is independently addressable — a later BA agent must be able to cite `D-2.2` and nothing else.

## D-2.1 — Multi-part stock check-and-reserve runs inside one `synchronized` block

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Raised in revision** | r1 |
| **Supersedes** | None |
| **Superseded by** | None |

**Context.** Scheduling a job reserves every part it needs against a single central stock pool held in `MockDataStore`, and the reservation is all-or-nothing: the whole booking is rejected unless every requested part is available (AC-2.2, AC-2.5). Reserving a part is a read-then-write — read `quantityOnHand - quantityReserved`, then increment `quantityReserved` — and it spans several parts in one request. `MockDataStore` is a plain in-memory `List` with no transactions, no constraints and no isolation; Spring Boot serves `POST /api/jobs` on a thread pool, so two bookings genuinely interleave. Feature 1's overlap check is itself an uncontrolled check-then-act (D-1.2), so there is no existing locking convention to inherit. The easy answer — check availability, then increment — is exactly what the concurrency criterion (AC-2.5) is written to break: two requests can both read the same availability and both reserve, driving combined `quantityReserved` past `quantityOnHand`.

**Decision.** `PartsReservationService.reserveForJob(List<RequiredPart>)` performs the entire sequence — resolve every `partId`, compute availability for every part, reject if any is short, and only then increment each `Part.quantityReserved` — inside a single `synchronized` block on one dedicated monitor owned by that service, so a job is never reserved against stock another concurrent job has already claimed and no partial reservation is ever left behind.

**Consequences.**
- Positive: the reservation invariant (`quantityReserved <= quantityOnHand`, all-or-nothing) is upheld by one clearly-bounded critical section that owns every mutation of the pool; the tester can assert AC-2.5 deterministically; no rollback/compensation logic exists to get wrong.
- Negative: all part reservations across the whole application serialize through one monitor — a real throughput ceiling under load, and coarser than strictly necessary (a request touching only part A still waits behind one touching only part B). Acceptable for an in-memory training boilerplate; not a pattern to carry to a real datastore. It also does nothing for Feature 1's separate, still-uncontrolled overlap race.
- **This constrains future features by:** any feature that mutates a `Part`'s `quantityReserved` or `quantityOnHand` (release-on-cancel, restock, consume-on-complete) must perform that mutation inside the same monitor, or the atomicity guarantee is void.

**Alternatives considered.**

| Alternative | Why not |
|---|---|
| Per-part atomics — an `AtomicInteger`/`ConcurrentHashMap.compute` per `Part`, reserving each independently | Gives per-part atomicity but not per-*request* atomicity. A request needing parts A and B could reserve A, then find B short, and must compensate by un-reserving A — a rollback window during which another request sees A wrongly reserved. The all-or-nothing rule (AC-2.2) makes multi-part atomicity the requirement, which per-part CAS cannot provide without exactly the lock this decision adds. |
| Optimistic, no lock — check availability, then increment, retrying on conflict | There is nothing to detect a conflict against: a `List<Part>` has no version or CAS on the composite (check-all, then write-all) operation. Two threads read the same availability and both proceed, so combined reservations exceed `quantityOnHand` — AC-2.5 fails outright. |
| Synchronize the whole `bookJob(...)` method (parts + Feature 1 overlap + save) on one lock | Correct but over-scoped: it serializes all bookings for all technicians behind parts contention and quietly changes Feature 1's concurrency behaviour, which is out of scope for this feature. Confining the lock to the parts critical section keeps the reservation invariant local to the code that owns it. |

**Revisit if:** `MockDataStore` is replaced by a real database (native transactions/row locks supersede this), a second independent writer of the parts pool appears outside `PartsReservationService`, or measured booking throughput makes the single global monitor a bottleneck — at which point a per-part or striped lock becomes worth its added complexity.

---

## D-2.2 — Parts errors reuse Feature 1's exception-to-status mapping

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Raised in revision** | r1 |
| **Supersedes** | None |
| **Superseded by** | None |

**Context.** `JobController` already maps an unknown `technicianId` to `404` and a time conflict to `409` through existing `catch` clauses on `IllegalArgumentException` and `IllegalStateException`, and rejects malformed `BookJobRequest` fields with `400` via Bean Validation. Parts add three new failure conditions — an unknown `partId`, an insufficient-but-known part, and a malformed `requiredParts` entry — each of which must surface to the caller with a status code (S-2.2, S-2.3, S-2.6). The choice is whether to reuse the controller's existing mapping or to give parts their own status codes / exception hierarchy.

**Decision.** In the job-booking flow, an unknown `partId` throws `IllegalArgumentException` → `404`, an insufficient-but-known part throws `IllegalStateException` → `409`, and a malformed `requiredParts` entry (missing `partId` or non-positive `quantity`) fails Bean Validation → `400`, reusing the exact mapping `JobController` already applies to unknown-technician (404) and time-conflict (409). No new `catch` clauses are added.

**Consequences.**
- Positive: error handling stays uniform with Feature 1; the developer adds no new `catch` clauses; every failure is assertable by status code plus `ApiError.message`.
- Negative: `409` now covers two distinct conditions — a Feature 1 time overlap and a parts shortage — distinguished only by `ApiError.message`. The review checked and accepted this as spec-sanctioned (S-2.2 mandates 409 for shortage), not a collapsed error mode.
- **This constrains future features by:** any later parts operation surfacing "not found", "conflict", or "bad input" must reuse this same three-way mapping (`IllegalArgumentException`→404, `IllegalStateException`→409, Bean Validation→400) rather than inventing new codes.

**Alternatives considered.**

| Alternative | Why not |
|---|---|
| Bespoke status codes or a new parts-specific exception hierarchy | Forks error handling for a single controller and forces new `catch` clauses, for no behavioural gain over the convention already in place. |
| Return `400` for insufficient stock (treat shortage as a bad request) | Conflates a transient conflict-with-current-state (correctly `409`) with a malformed payload (`400`, reserved for validation failures per S-2.6); would make the shortage and malformed cases indistinguishable by status code. |

---

## D-2.3 — `Job` and `BookJobRequest` gain `requiredParts` as a backward-compatible, empty-by-default extension

| Field | Value |
|---|---|
| **Status** | Accepted |
| **Raised in revision** | r1 |
| **Supersedes** | None |
| **Superseded by** | None |

**Context.** `Job` and `BookJobRequest` are existing Feature 1 types with live callers: `MockDataStore.seed()`, Feature 1's booking path, and the frontend `Job` model. Recording required parts means extending both shapes. The choice is whether to extend them additively or to change the `Job` constructor signature outright.

**Decision.** `Job` and `BookJobRequest` are extended with a `requiredParts` field that is empty/absent by default; `Job`'s existing 6-argument constructor is retained (delegating `requiredParts` to an empty list) alongside a new 7-argument constructor, so every existing caller — including `MockDataStore.seed()` and Feature 1's booking path — compiles and behaves unchanged.

**Consequences.**
- Positive: every existing caller compiles and behaves unchanged; the change is migration-safe; readers of `Job.requiredParts` never see `null`.
- Negative: `Job` now carries two constructors to maintain, and callers must know which to use.
- **This constrains future features by:** treat `requiredParts` as optional with an empty default when reading a `Job`, since jobs created before or without parts carry an empty list, never null-by-contract.

**Alternatives considered.**

| Alternative | Why not |
|---|---|
| Replace `Job`'s constructor signature to require `requiredParts` | Breaks `MockDataStore.seed()` and Feature 1's call site, coupling an additive inventory feature to a needless edit of working scheduling code. |
| Make `requiredParts` nullable (`null` = none) rather than empty-by-default | Pushes a null-check onto every reader; an always-empty-never-null list is the safer contract and keeps existing consumers unaffected (migration-safety). |
