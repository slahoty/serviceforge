# Feature 1 — Decision Log

Decisions made while building Feature 1 that later features may need to retrieve. This file exists so a later feature's memory-retrieval step has something concrete to pull a single fact from, instead of needing the entire build history in context.

## Decision: standard travel buffer per job is 45 minutes

**What was decided:** every booked job reserves not just its own start/end time on a technician's calendar, but an additional 45-minute travel buffer, to account for the technician getting to the next job. This is encoded as `TRAVEL_BUFFER_MINUTES = 45` in `TechnicianAvailabilityService`.

**Why it matters beyond Feature 1:** this buffer is what actually limits how many jobs — and, later, how many parts-reservations tied to those jobs — can realistically be scheduled for one technician in a day. Anything built on top of the calendar that reasons about "how many jobs can fit today" needs this number, not a guess.

**Where it lives in code:** `backend/src/main/java/com/serviceforge/service/TechnicianAvailabilityService.java`, `TRAVEL_BUFFER_MINUTES` constant.

## Decision: overlap detection compares exact start times, not intervals

**What was decided (and should probably be reconsidered):** the current overlap check in `bookJob(...)` rejects a new booking only if its start time exactly matches an existing booking's start time. It does not check whether the two time ranges genuinely overlap.

**Why it matters:** this is a live bug, not a design choice anyone defended. It means two jobs with different-but-overlapping start times for the same technician are both silently accepted. Anything that assumes "a technician's booked slots never overlap" is currently assuming something the code doesn't guarantee.
