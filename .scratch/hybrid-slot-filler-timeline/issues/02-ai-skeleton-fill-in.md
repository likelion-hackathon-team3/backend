# 02 — AI Prompt Skeleton Fill-in & Flex Interval Enrichment

**What to build:**
Update the AI prompt templates (`timeline_future.st`, `timeline_today.st`, `timeline_critic.st`) and `TimelineAiGenerator` to accept the pre-calculated schedule skeleton and flex intervals. The AI populates empathetic titles, detailed descriptions, and highlight tips for each fixed slot while keeping their exact timestamps and categories intact. For flex intervals, the AI generates personalized leisure/rest activities based on user notes and fatigue levels.

**Blocked by:** 01 — Deterministic Slot Calculator for Mandatory Shift Activities

**Status:** ready-for-agent

- [ ] AI prompt templates receive the pre-calculated slot skeleton and flex intervals
- [ ] Prompt strictly instructs AI to maintain fixed slot timestamps and categories
- [ ] AI generates 1–2 personalized sub-activities within unallocated flex intervals
- [ ] AI generates empathetic page titles, subtitles, and actionable recommendations
- [ ] Unit tests verifying prompt injection and schema deserialization
