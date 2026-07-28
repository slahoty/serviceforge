# Bug: Technician Calendar Overlapping Bookings Silently Accepted

## 🐞 Bug Description
When scheduling jobs on the technician availability calendar, the system allows multiple jobs to be assigned to the same technician during overlapping time slots. Instead of rejecting the conflicting job and displaying an error, the system silently accepts both bookings. This leads to double-booking and operational scheduling issues.

## 🔄 Steps to Reproduce
1. Log in to the scheduling portal.
2. Select **Technician A**.
3. Create and assign **Job 1** to Technician A for **10:00 AM – 12:00 PM** on the target date. Save the job.
4. Create a second job, **Job 2**, for the same date and set the time to **11:00 AM – 01:00 PM** (which overlaps with Job 1).
5. Attempt to assign and save **Job 2** to **Technician A**.

## ❌ Current Behavior
The system saves and schedules **Job 2** successfully. Both jobs display concurrently on Technician A's calendar without any warnings, errors, or validation flags.

## 🟢 Expected Behavior
The system should detect the schedule overlap for **Technician A**. It must reject the creation/saving of **Job 2** and display a clear validation error indicating a scheduling conflict.

---
