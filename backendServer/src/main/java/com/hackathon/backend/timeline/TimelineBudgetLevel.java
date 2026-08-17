package com.hackathon.backend.timeline;

// timelineStart~timelineEnd 가용시간 등급.
// AMPLE(넉넉함) >= 12시간, MODERATE(보통) 8~12시간, TIGHT(촉박함) < 8시간.
enum TimelineBudgetLevel {
    AMPLE,
    MODERATE,
    TIGHT
}
