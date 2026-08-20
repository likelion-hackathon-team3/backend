---
labels: ["ready-for-agent"]
---

## Problem Statement

The AI timeline generation system currently relies on `HH:mm` strings for time representation. Even though the API request provides full `LocalDateTime` bounds (`currentWorkEnd` and `nextWorkStart`), the backend strips the dates away. This forces the system to use a complex `+1440` minute offset hack to guess whether an event crosses midnight, leading to sorting bugs. It also forces the AI to schedule events without knowing the exact duration of the nurse's free time, causing logical errors and requiring a sequential Critic AI to fix them.

## Solution

Migrate the timeline system to fully embrace ISO-8601 `LocalDateTime` representations. The backend will calculate the exact "Total Free Time" (Duration) using the exact start and end dates provided in the request. Both the `LocalDateTime` bounds and the calculated free time will be passed to the AI (Generator and Critic) to serve as hard constraints. The AI will output timeline events using ISO-8601 strings. This completely removes the need for offset sorting hacks, ensures the AI is aware of its temporal boundaries, and maintains the safety net of the Critic AI.

## User Stories

1. As a nurse, I want my timeline events to include full dates, so that I clearly know which events happen today and which happen tomorrow.
2. As a nurse, I want the AI to perfectly balance my activities within my actual free time, so that I don't get unrealistic schedules that overcommit my rest periods.
3. As a frontend developer, I want to receive standard ISO-8601 date strings, so that I can easily parse, format, and render times without guessing midnight rollovers.
4. As a backend developer, I want to remove the `parseAnchorMinutes` and `+1440` hack, so that the sorting logic is a simple, bug-free `LocalDateTime` comparison.
5. As a backend developer, I want the Generator and Critic AIs to receive the total free hours, so that they are strictly bounded and less likely to hallucinate chronological errors.

## Implementation Decisions

- **API Contracts**: The response DTO (`TimelineItemDto`) will change its `time` field from `HH:mm` to ISO-8601 format (e.g., `2026-08-17T15:00`).
- **Architectural Decisions**: 
  - The sequential Generator -> Critic AI pipeline will be retained for maximum safety.
  - The backend will pre-calculate the total free time as a duration (in hours or minutes) if both `currentWorkEnd` and `nextWorkStart` are valid ISO-8601 strings.
- **Specific Interactions**: The calculated total free time will be injected into the Prompt map. The `timeline_future.st`, `timeline_today.st`, and `timeline_critic.st` templates will be updated to instruct the AI to respect this duration and output ISO-8601 dates.
- **Modules Modified**: `TimelineItemDto`, `TimelineServiceImpl`, `TimelineAiGenerator`, and all `.st` prompt templates.

## Testing Decisions

- **Test Seam**: The seams remain the same: `TimelineAiGenerator` (for AI interaction) and `TimelineServiceImpl` (for sorting and integration).
- **Good Tests**: The tests must verify that `TimelineServiceImpl` correctly sorts items that span across midnight using natural `LocalDateTime` comparison, without relying on anchor offsets. We must also verify that `TimelineAiGenerator` correctly calculates the `totalFreeHours` and injects it into the rendered prompt.
- **Prior Art**: We will modify existing tests (`TimelineServiceImplTest`, `TimelineAiGeneratorTest`) by changing the mock input data from `HH:mm` strings to ISO-8601 strings, and updating the expected prompt assertions.

## Out of Scope

- Persisting the generated timelines to a database (`TimelineEntity`).
- Modifying the OCR pipeline or any other feature outside of timeline generation.
- Changing the frontend implementation (this spec covers the backend API and AI logic).

## Further Notes

- Changing the API contract (`TimelineItemDto`) is a breaking change for any clients currently consuming the `HH:mm` format. Coordinate with frontend teams.
