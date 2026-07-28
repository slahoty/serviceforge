# Feature 1 — Decision Log

Decisions made while building Feature 1 that later features may need to retrieve. This file exists so a later feature's memory-retrieval step has something concrete to pull a single fact from, instead of needing the entire build history in context.

Each decision is addressable by ID (`D-1.x`). A later feature's spec cites the ID in its `Depends on` cell and restates the **This constrains future features by** clause in one clause — that is the only supported way to declare a dependency on Feature 1 (see `pipeline/orchestration.md`).

## D-1.1 — Standard travel buffer per job is 45 minutes

**What was decided:** every booked job reserves not just its own start/end time on a technician's calendar, but an additional 45-minute travel buffer, to account for the technician getting to the next job. This is encoded as `TRAVEL_BUFFER_MINUTES = 45` in `TechnicianAvailabilityService`.

**Why it matters beyond Feature 1:** this buffer is what actually limits how many jobs — and, later, how many parts-reservations tied to those jobs — can realistically be scheduled for one technician in a day. Anything built on top of the calendar that reasons about "how many jobs can fit today" needs this number, not a guess.

**Where it lives in code:** `backend/src/main/java/com/serviceforge/service/TechnicianAvailabilityService.java`, `TRAVEL_BUFFER_MINUTES` constant.

**This constrains future features by:** a technician's daily job capacity is bounded by the 45-minute `TRAVEL_BUFFER_MINUTES` between consecutive jobs; any feature reasoning about how many jobs fit a technician's day must use this value, not re-derive it.

## D-1.2 — Overlap detection compares exact start times, not intervals

**What was decided (and should probably be reconsidered):** the current overlap check in `bookJob(...)` rejects a new booking only if its start time exactly matches an existing booking's start time. It does not check whether the two time ranges genuinely overlap.

**Why it matters:** this is a live bug, not a design choice anyone defended. It means two jobs with different-but-overlapping start times for the same technician are both silently accepted. Anything that assumes "a technician's booked slots never overlap" is currently assuming something the code doesn't guarantee.

**Where it lives in code:** `backend/src/main/java/com/serviceforge/service/TechnicianAvailabilityService.java`, the overlap check inside `bookJob(...)`.

**This constrains future features by:** the `bookJob(...)` booking path is an uncontrolled check-then-act and does not guarantee non-overlapping slots for a technician; any feature that hooks into `bookJob(...)` must not assume booking-level serialization and must guard its own invariants independently.
