---
labels: ["ready-for-agent"]
---

## Problem Statement

The AI sometimes generates schedules and timelines with chronological errors (e.g., suggesting a wake-up time that occurs after the work shift has already started). The current single-prompt AI generation approach is not robust enough to guarantee the prevention of logical time paradoxes. Additionally, the backend currently lacks a mechanism to safely and automatically fix these hallucinations without failing the request or applying risky offset calculations.

## Solution

Implement a **Sequential AI Pipeline (Generator -> Critic)** for timeline creation. Instead of relying on a single AI generation step, the system will use a two-step process. First, the Generator AI drafts the timeline. Then, a Critic AI reviews the drafted timeline, automatically patches any chronological paradoxes or logical impossibilities, and returns a sanitized, perfectly ordered timeline JSON to the user. The timeline remains ephemeral (not saved to a database).

## User Stories

1. As a nurse, I want my generated timeline to be chronologically accurate, so that I don't see impossible schedules like sleeping after my shift starts.
2. As a nurse, I want to receive a complete timeline without system errors, so that I can immediately trust and use the suggested schedule.
3. As a user, I want the system to automatically fix any generated schedule anomalies in the background, so that I don't have to manually regenerate the timeline.
4. As a developer, I want the Critic AI to automatically fix logic errors rather than just throwing an exception, so that the API always returns a valid timeline response.
5. As a developer, I want to retain the `HH:mm` time representation and `+1440` offset logic, so that the existing backend architecture doesn't require a massive rewrite to `LocalDateTime`.

## Implementation Decisions

- **Modules Modified**: `TimelineAiGenerator` and potentially `TimelineServiceImpl`.
- **Architectural Change**: Transition from a single LLM call to a sequential two-call LLM pipeline (Generator -> Critic).
- **Process Flow**:
  1. **Generator Phase**: `TimelineAiGenerator` calls the LLM with the existing prompt (`timeline_today.st` or `timeline_future.st`) to produce a draft JSON timeline.
  2. **Critic Phase**: `TimelineAiGenerator` immediately makes a second LLM call, passing the drafted JSON to a new Critic prompt.
  3. **Critic Role**: The Critic AI will act as a "Fixer". If it detects chronological anomalies or missing critical blocks, it will patch the times and return the final, sanitized JSON.
- **Time Model**: The system will continue to use the `HH:mm` string format for time. The backend's `+1440` minute offset sorting logic (recently patched for bugs) will handle the rendering order.
- **Persistence**: Timelines will remain ephemeral (On-the-fly generation) and will not be persisted to a database.

## Testing Decisions

- **Test Seam**: The primary test seams are the `TimelineAiGenerator` (for the dual-LLM interaction) and `TimelineServiceImpl` (for the final sorting and presentation). This is the highest logical seam for timeline generation.
- **Good Tests**: Tests should verify external behavior. We will mock the `ChatModel` to simulate a "broken" timeline response from the Generator AI, and then verify that the Critic AI prompt is properly constructed and invoked to fix the broken JSON.
- **Prior Art**: We will look at existing tests like `TimelineServiceImplTest` and `TimelineAiGeneratorTest` to understand how the `ChatModel` and prompts are currently mocked and tested.

## Out of Scope

- Persisting the generated timelines to a database (`TimelineEntity`).
- Refactoring the time representation from `HH:mm` strings to `LocalDateTime` objects.
- Implementing an automatic retry loop (the Critic is responsible for patching on the first pass).
- Adding a user interface for manually editing the timeline.

## Further Notes

- The sequential pipeline will increase the latency of the API endpoint. We are accepting this trade-off for the sake of guaranteeing timeline accuracy and chronological integrity without complex backend date-math refactoring.
