package com.hackathon.backend.timeline;

// Timeline 내부 전용 분류. API 응답에는 노출하지 않는다.
// Analysis의 transitionType/WorkTransitionPattern과는 별개 개념이다.
enum TimelineGroup {
    WORK_TO_WORK,
    WORK_TO_OFF,
    OFF_TO_WORK,
    OFF_TO_OFF
}
