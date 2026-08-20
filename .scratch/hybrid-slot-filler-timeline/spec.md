---
labels: ["ready-for-agent"]
---

## Problem Statement

When generating daily nurse wellness timelines solely through an LLM, the model occasionally hallucinates timing overlaps, impossible durations (e.g., 14-hour sleep blocks before afternoon shifts), insufficient sleep periods during short turnarounds, or past events when an evening `currentTime` is provided. Even with prompt constraints and critic layers, pure LLMs struggle with precise mathematical reverse scheduling and strict temporal boundaries across 16 different nurse shift transitions.

## Solution

Implement a **Hybrid Slot Filler Architecture with Adaptive Duration Bounds and Commute-Aware Scheduling**. 

A deterministic backend engine (`TimelineSlotCalculator`) calculates the exact `LocalDateTime` timestamps for essential activities (sleep, pre-shift naps, meal timings, pre-work preparation, commute/work start) using clinical biological rhythm guidelines and reverse scheduling formulas. 

Key capabilities:
1. **Commute-Aware Activity Horizon**: Activities begin after returning home (`workEnd + commuteMinutes`) or from `currentTime` if already later, strictly filtering out any past events.
2. **Standard Duration Ranges & Sleep Cap**: Standard nighttime sleep is bounded between 6.5h and 8.5h (default 7.5h) to eliminate 10–14h sleep hallucinations. Remaining daytime is allocated to meals and flex intervals.
3. **Adaptive Short-Interval Sleep (0h Minimum)**: When turnaround is extremely short (e.g. 2.5 hours), the engine avoids forcing 5.5h of sleep and instead adaptively switches to a short power nap (30–90m NAP) or restful preparation (REST), supporting a true 0-hour minimum sleep without schedule overlap.
4. **Split-Sleep for NIGHT to DAY**: Organizes recovery sleep into a 1st daytime sleep (08:30–13:30) and a 2nd early night sleep (22:30–05:30) to stabilize circadian rhythms.
5. **Direct High-Speed AI Generation**: The pre-calculated schedule skeleton (`skeletonJson`) and flex intervals (`flexIntervals`) are passed to the AI (`TimelineAiGenerator` with `gpt-4o`), which enriches the timeline with empathetic titles, detailed descriptions, recovery tips, and personalized flex activities based on user notes.

## User Stories

1. As a nurse working irregular 3-shifts, I want my mandatory rest, meal, and sleep schedules to be mathematically guaranteed without any time overlap or impossible durations, so that I can reliably follow a healthy routine.
2. As a nurse checking my schedule in the evening after work, I want the timeline to begin from my current time or my actual arrival at home, so that I am not shown already-passed daytime events.
3. As a nurse transitioning to an afternoon (EVENING) shift, I want my sleep duration capped at a healthy 7.5–8.5 hours rather than sleeping half the day, with my morning/lunch time open for friends or leisure.
4. As a nurse with an ultra-short turnaround (e.g., 2.5 hours before an emergency shift), I want the system to suggest a short 40–90 minute power nap or rest instead of attempting to schedule impossible 6-hour sleep blocks.
5. As a nurse finishing a NIGHT shift and working a DAY shift next, I want my rest divided into a morning recovery sleep and an early evening bedtime, so that my body can adjust to the day schedule.
6. As a nurse with personal preferences (e.g., pilates, sensitive to caffeine), I want the AI to suggest personalized activities during open flex hours and clearly warn me about caffeine cutoffs after the cutoff time.
7. As a backend developer, I want all temporal calculations (commute offsets, sleep bounds, reverse prep formulas) centralized in a pure deterministic calculator with clear constant ranges, ensuring 100% predictable schedules.
8. As a backend developer, I want the AI prompt to receive a fixed skeleton of timestamps and categories, so that LLM hallucinations regarding schedule order or time math are completely eliminated.

## Implementation Decisions

- **Deterministic Slot Engine (`TimelineSlotCalculator`)**:
  - Located in `com.likeLion.backend.aiserver.service.layer`.
  - Defines explicit duration range constants:
    - Sleep: `MIN_SLEEP_MINUTES = 0L`, `DEFAULT_SLEEP_MINUTES = 450L` (7.5h), `MAX_SLEEP_MINUTES = 510L` (8.5h).
    - NAP: `MIN_NAP_MINUTES = 30L`, `DEFAULT_NAP_MINUTES = 90L`.
    - Meal: `MIN_MEAL_MINUTES = 20L`, `DEFAULT_MEAL_MINUTES = 40L`, `MAX_MEAL_MINUTES = 45L`.
    - Preparation: `MIN_PREP_MINUTES = 20L`, `DEFAULT_PREP_MINUTES = 30L`.
  - Calculates `homeArrival = workEnd.plusMinutes(commuteMinutes)`.
  - Calculates `effectiveStart = max(currentTime, homeArrival)`.
  - Reverse-calculates preparation time from `nextWorkStart - commuteMinutes - prepDuration`.
  - Past slots strictly filtered out (`slot.time() < effectiveStart`).
  - Gaps >= 3 hours registered as `flexIntervals`.

- **Prompt & AI Generator (`TimelineAiGenerator` & Prompts)**:
  - Injects `skeletonJson`, `flexIntervals`, and `totalFreeHours` into prompt templates (`timeline_future.st`, `timeline_today.st`).
  - AI keeps fixed `time` and `category` from `skeletonJson` and populates `title`, `description`, `highlight`, and `recommendations`.
  - AI fills `flexIntervals` with 1–2 personalized activities (REST, EXERCISE, NAP) tailored to `userNotes` and fatigue levels.
  - Clear prompt instructions that `{adjustedCaffeineCutoff}` warnings must state "avoid caffeine *after* this time".
  - Configured with `gpt-4o` for high reliability and single-pass rapid execution.

- **Service Orchestration (`TimelineServiceImpl`)**:
  - Single-constructor Spring dependency injection (`@Service`).
  - Pipeline: `Request` ➡️ `TimelineSlotCalculator.calculateSkeleton(...)` ➡️ `TimelineAiGenerator.generate(...)` ➡️ ISO-8601 sorting & category fallback ➡️ `TimelineGenerateResponse`.

## Testing Decisions

- **Test Seam 1: `TimelineSlotCalculatorTest` (Deterministic Temporal Rules)**:
  - Test `DAY_TO_NIGHT` (commute & pre-shift 90m nap reverse-calculation).
  - Test `EVENING_TO_DAY` (short turnaround with prioritized sleep).
  - Test `DAY_TO_EVENING` with `currentTime` boundary (past events excluded, sleep capped at 7.5h, lunch & flex interval scheduled).
  - Test ultra-short turnaround (2.5h available time creates short NAP/REST with 0h min sleep and no time collisions).

- **Test Seam 2: `TimelineAiGeneratorTest` (Prompt Rendering & DTO Mapping)**:
  - Verify model map generation with skeleton JSON, flex intervals, commute minutes, and personalization buffer.
  - Verify parsing of AI response into `RawTimelineAiResponse`.

- **Test Seam 3: `TimelineServiceImplTest` (Service Layer Integration)**:
  - Verify end-to-end orchestration with mocked LLM output and spy slot calculator.

## Out of Scope

- Database persistence of generated schedules.
- Direct UI calendar synchronization.
- Complex ML-based sleep optimization algorithms (heuristic rule-based calculations are sufficient).

## Further Notes

- The API response format strictly utilizes `TimelineItemDto` with `YYYY-MM-DDTHH:mm` ISO-8601 timestamps, maintaining full compatibility with frontend expectations.
