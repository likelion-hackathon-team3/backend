package com.hackathon.backend.timeline;

import java.time.Duration;
import java.time.LocalDateTime;

// 가용시간(timelineStart~timelineEnd)을 3단계 등급으로 분류하는 순수 계산 클래스.
// 확정된 임계값: 넉넉함 >= 720분(12h), 보통 480~719분(8~12h), 촉박함 < 480분(8h).
class TimelineBudgetClassifier {

    private static final long AMPLE_MINUTES = 12 * 60;
    private static final long MODERATE_MINUTES = 8 * 60;

    TimelineBudgetLevel classify(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes >= AMPLE_MINUTES) {
            return TimelineBudgetLevel.AMPLE;
        }
        if (minutes >= MODERATE_MINUTES) {
            return TimelineBudgetLevel.MODERATE;
        }
        return TimelineBudgetLevel.TIGHT;
    }
}
