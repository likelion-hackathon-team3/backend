package com.hackathon.backend.timeline;

import java.time.LocalDateTime;

// TimelineRangeCalculator가 계산한 결과.
// timelineStart/timelineEnd: 웰니스 아이템을 배치할 범위.
// nextWorkActualStart: 다음 실제 근무 시작 시각. WORK_TO_WORK/OFF_TO_WORK에서만 존재하며
//   WORK 마커 아이템 위치로 쓰인다. timelineEnd(=actualStart-commute) 이후일 수 있는
//   유일한 예외 지점이다. WORK_TO_OFF/OFF_TO_OFF에서는 null(다음 근무 없음).
record TimelineRange(
        TimelineGroup group,
        LocalDateTime timelineStart,
        LocalDateTime timelineEnd,
        LocalDateTime nextWorkActualStart
) {
    boolean hasUsableWindow() {
        return timelineEnd.isAfter(timelineStart);
    }
}
