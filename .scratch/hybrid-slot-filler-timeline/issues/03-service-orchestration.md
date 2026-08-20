# 03 — Service Orchestration & End-to-End Timeline Integration

**What to build:**
Integrate `TimelineSlotCalculator` and `TimelineAiGenerator` within `TimelineServiceImpl`. Orchestrate the pipeline from incoming request to base slot calculation, AI enrichment, post-sanitization, and final response assembly. Ensure full backward compatibility with the existing API response schema and test end-to-end flows.

**Blocked by:** 02 — AI Prompt Skeleton Fill-in & Flex Interval Enrichment

**Status:** ready-for-agent

- [ ] `TimelineServiceImpl` orchestrates slot calculation followed by AI generation
- [ ] Merges fixed slots and AI-generated flex activities in strict chronological `LocalDateTime` order
- [ ] Category sanitization and fallback mechanisms preserved
- [ ] End-to-end integration and unit tests passing across all test suites
