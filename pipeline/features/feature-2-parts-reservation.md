# Feature 2 — Parts Reservation

| Field | Value |
|---|---|
| **Status** | Candidate-built / in progress |
| **One-line intent** | Allow a technician to reserve parts for a job when booking that job, ensuring parts inventory is decremented and a technician cannot book parts that are out of stock. |
| **Depends on** | Feature 1, D-1.1: job scheduling writes a `scheduled_job` row (a booked job) before any downstream call; Feature 1 establishes the job booking entry point that parts reservation extends. |
| **In scope** | Reserve parts against a booked job; decrement parts inventory when a reservation succeeds; reject a job booking if any required part is out of stock; list reserved parts for a job; persist part reservations tied to job lifecycle. |
| **Out of scope** | Parts catalog management (adding/editing/deleting part types); inventory replenishment or reorder triggers; parts-return or job-cancellation workflows (handled in a separate feature); alerts when stock runs low; parts cost or billing. |
| **Definition of Done** | A technician can reserve one or more parts when booking a job; the system rejects a job booking if any part is out of stock; reserved parts are visible when listing a job's details; inventory count is decremented by the reservation count; a part reservation persists for the lifetime of the booked job. |

## User Story

**US-2.1**

**As a** dispatcher,
**I want to** reserve required parts when I book a technician to a job,
**so that** I know the parts will be available when the technician arrives and the inventory is accurate.

**INVEST check:** Independent <yes> · Negotiable <yes> · Valuable <yes> · Estimable <yes> · Small <yes> · Testable <yes>

### Scenarios

#### Happy path — S-2.1

**Given** a job is ready to be booked for a technician, and all required parts have sufficient stock (each part has quantity ≥ the amount needed),
**When** the dispatcher books the job and specifies the parts to reserve,
**Then** the job is booked successfully, each part's inventory is decremented by the reserved quantity, and the parts reservation is persisted and linked to the job.

#### Out of stock — S-2.2

**Given** a job is ready to be booked, but one or more required parts have insufficient stock (requested quantity > available quantity),
**When** the dispatcher attempts to book the job with those parts,
**Then** the entire job booking is rejected with a `400 Bad Request` error, the error body names which part(s) are out of stock and how much is needed vs. available, and no inventory is decremented.

#### Partial stock — S-2.3

**Given** a job requires parts A (qty 5) and B (qty 3), part A has 10 in stock but part B has only 2 in stock,
**When** the dispatcher attempts to book the job,
**Then** the booking is rejected, the error specifies that part B is short by 1 unit, and no inventory changes are made (all-or-nothing atomicity).

#### Zero stock edge case — S-2.4

**Given** a job requires a part that has exactly 0 in stock,
**When** the dispatcher attempts to book the job,
**Then** the job booking is rejected with a clear "out of stock" error for that part.

#### Exact stock match — S-2.5

**Given** a job requires a part in an exact quantity that matches the current stock (e.g., part X has 5 in stock and the job needs exactly 5),
**When** the dispatcher books the job and reserves all 5 units,
**Then** the job is booked successfully, inventory for part X becomes 0, and the reservation is persisted.

### Acceptance Criteria

- [ ] **AC-2.1** — When a job is booked with parts, each part's inventory is decremented by the reserved quantity and the new inventory is persisted. *(covers S-2.1)*
- [ ] **AC-2.2** — When any required part has insufficient stock, the entire job booking fails atomically (no partial inventory decrements occur). *(covers S-2.2, S-2.3, S-2.4)*
- [ ] **AC-2.3** — An out-of-stock rejection error includes the part name, the requested quantity, and the current available quantity. *(covers S-2.2)*
- [ ] **AC-2.4** — A part reservation is queryable by job ID and returns the list of reserved parts with their quantities. *(covers S-2.1)*
- [ ] **AC-2.5** — When inventory reaches exactly 0 after a reservation, subsequent bookings that require that part are rejected. *(covers S-2.5)*
- [ ] **AC-2.6** — A part reservation persists for the lifetime of the booked job (reservations are not lost on restart or state reload). *(covers S-2.1)*

## Assumptions

| ID | Assumption | Basis | Impact if wrong |
|---|---|---|---|
| A-2.1 | Parts have a unique identifier and a current stock quantity. | Job booking in Feature 1 targets a technician; parts must be identifiable entities with inventory tracking. | System cannot distinguish which part is out of stock or correctly decrement inventory. |
| A-2.2 | Job booking is the only entry point for part reservations (parts are not reserved independently). | Feature 1 establishes job booking as the scheduling entry point; extending it keeps the model coherent. | Parts could be reserved in isolation, breaking the job-to-parts binding and making inventory state non-deterministic. |
| A-2.3 | A reservation is tied to a specific job and cannot be transferred to another job. | Jobs are discrete work orders; a part reserved for Job A should not fulfill Job B's needs. | Double-reservation and inventory corruption. |
| A-2.4 | Stock quantities are non-negative integers (no fractional parts). | Standard inventory model; parts are discrete units. | Rounding errors and decimal representation bugs. |

## Traceability

| DoD item | Satisfied by |
|---|---|
| A technician can reserve one or more parts when booking a job | AC-2.1, AC-2.4 |
| The system rejects a job booking if any part is out of stock | AC-2.2, AC-2.3 |
| Reserved parts are visible when listing a job's details | AC-2.4 |
| Inventory count is decremented by the reservation count | AC-2.1, AC-2.5 |
| A part reservation persists for the lifetime of the booked job | AC-2.6 |

## Artifacts this feature touches

*(to be filled in by developer and tester agents)*
