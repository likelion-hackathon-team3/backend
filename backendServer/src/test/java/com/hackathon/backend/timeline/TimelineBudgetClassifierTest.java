package com.hackathon.backend.timeline;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// TimelineBudgetClassifier 경계값 검증.
// 확정 규칙: AMPLE >= 12h, MODERATE >= 8h && < 12h, TIGHT < 8h.
class TimelineBudgetClassifierTest {

    private final TimelineBudgetClassifier classifier = new TimelineBudgetClassifier();
    private final LocalDateTime start = LocalDateTime.of(2026, 8, 17, 0, 0);

    @Test
    void 열두시간_정확히는_AMPLE() {
        assertThat(classifier.classify(start, start.plusHours(12))).isEqualTo(TimelineBudgetLevel.AMPLE);
    }

    @Test
    void 열두시간_초과는_AMPLE() {
        assertThat(classifier.classify(start, start.plusHours(12).plusMinutes(1))).isEqualTo(TimelineBudgetLevel.AMPLE);
    }

    @Test
    void 열한시간59분은_MODERATE() {
        assertThat(classifier.classify(start, start.plusHours(11).plusMinutes(59))).isEqualTo(TimelineBudgetLevel.MODERATE);
    }

    @Test
    void 여덟시간_정확히는_MODERATE() {
        assertThat(classifier.classify(start, start.plusHours(8))).isEqualTo(TimelineBudgetLevel.MODERATE);
    }

    @Test
    void 일곱시간59분은_TIGHT() {
        assertThat(classifier.classify(start, start.plusHours(7).plusMinutes(59))).isEqualTo(TimelineBudgetLevel.TIGHT);
    }

    @Test
    void 영_또는_음수_구간도_TIGHT로_처리된다() {
        assertThat(classifier.classify(start, start)).isEqualTo(TimelineBudgetLevel.TIGHT);
        assertThat(classifier.classify(start, start.minusHours(1))).isEqualTo(TimelineBudgetLevel.TIGHT);
    }
}
