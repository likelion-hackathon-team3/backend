# 01 — Deterministic Slot Calculator for Mandatory Shift Activities

**What to build:**
A deterministic time calculation engine (`TimelineSlotCalculator`) that takes a nurse's shift transition request and computes a list of fixed time slots for essential activities (sleep, pre-work nap, meals, preparation, work) with exact `LocalDateTime` timestamps. In short turnarounds (e.g., EVENING to DAY), it protects vital sleep duration (minimum 5.5 hours) by elastically compressing preparation and meals, and accurately identifies any remaining unallocated time as flex intervals.

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

- [ ] Calculates exact `LocalDateTime` start times for essential activities across the 16 nurse shift transitions
- [ ] Applies reverse-scheduling logic for preparation and commute based on the next shift start
- [ ] Implements priority-based compression to guarantee protected sleep time in tight turnarounds
- [ ] Extracts unallocated time gaps between mandatory slots as flexible intervals
- [ ] Comprehensive unit tests covering shift patterns and edge cases
