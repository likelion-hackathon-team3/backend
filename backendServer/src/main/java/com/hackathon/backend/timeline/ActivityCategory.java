package com.hackathon.backend.timeline;

// GET /api/timeline 응답 timelineItems[].category 값.
// 외부 API 계약에 노출되는 값만 정의한다. SLEEP_PREP/RECOVERY_SLEEP 같은 내부 세분화는
// ActivityKind로만 구분하고, category는 이 8개로만 매핑한다(프론트 계약 임의 확장 금지).
// CAFFEINE/FREE는 별도 category로 만들지 않는다(팀 협의 확정: 카페인은 필요하면 PREPARATION
// 항목의 문구로만 표현하고, 행동이 필요 없는 시간은 item 자체를 만들지 않는다).
public enum ActivityCategory {
    MEAL,
    REST,
    EXERCISE,
    NAP,
    SLEEP,
    PREPARATION,
    WAKE_UP,
    WORK
}
