package com.hackathon.backend.personalization;

import com.hackathon.backend.feedback.Feedback;
import com.hackathon.backend.feedback.FeedbackRepository;
import com.hackathon.backend.personalization.dto.PersonalizationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// PersonalizationService(GET /api/personalization 개인화 보정값 조회) 로직 테스트.
// @Transactional : 각 테스트가 끝나면 DB 변경을 자동으로 되돌려 서로 영향을 주지 않게 한다.
@SpringBootTest
@Transactional
class PersonalizationServiceTest {

    private static final String TRANSITION = "NIGHT_TO_OFF";

    @Autowired
    PersonalizationService personalizationService;

    @Autowired
    FeedbackRepository feedbackRepository;

    // transitionType을 직접 주입한다(Schedule 없이도 Feedback.transitionType만 있으면 계산 대상이 됨).
    private void feedback(String feedbackDate, String transitionType, double sleep,
                           boolean caffeineTaken, String lastCaffeineTime,
                           int fatigue, int routine) {
        feedbackRepository.save(new Feedback(
                LocalDate.parse(feedbackDate),
                transitionType,
                sleep,
                caffeineTaken,
                lastCaffeineTime == null ? null : LocalTime.parse(lastCaffeineTime),
                fatigue,
                routine,
                LocalDateTime.now()));
    }

    // 1. 동일 transitionType Feedback 없음 -> 기본 응답
    @Test
    void 동일전환_Feedback_없으면_기본응답() {
        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.isRepeatedPatternFound()).isFalse();
        assertThat(res.getRecommendedRoutineNotice()).isEqualTo("축적된 피드백이 없어 기본 추천 기준을 적용합니다.");
    }

    // 2. 수면 5.5시간 Feedback 1건 -> recommendedSleepBuffer = 60, repeatedPatternFound = false
    @Test
    void 수면부족_1건이면_60분_보정_반복아님() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(60);
        assertThat(res.isRepeatedPatternFound()).isFalse();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족이 확인되어 추가 수면 시간을 반영합니다.");
    }

    // 3. 높은 피로도 8 이상, 수면은 6시간 이상 -> recommendedSleepBuffer = 30
    @Test
    void 수면정상_고피로만이면_30분_보정() {
        feedback("2026-08-01", TRANSITION, 7.0, false, null, 8, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30);
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 높은 피로도가 확인되어 추가 회복 시간을 반영합니다.");
    }

    // 4. 수면 부족 + 높은 피로 동시 존재 -> recommendedSleepBuffer = 60 (90분으로 누적되지 않음)
    @Test
    void 수면부족과_고피로_동시_존재시_60분만_적용() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(60);
    }

    // 5. caffeineTaken=true, lastCaffeineTime=15:00, postShiftFatigue=8 -> adjustedCaffeineCutoff = "14:30"
    @Test
    void 카페인섭취_고피로_동반시_30분_당긴_cutoff() {
        feedback("2026-08-01", TRANSITION, 7.0, true, "15:00", 8, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isEqualTo("14:30");
    }

    // 6. caffeineTaken=false -> adjustedCaffeineCutoff = null
    @Test
    void 카페인_미섭취면_cutoff_null() {
        feedback("2026-08-01", TRANSITION, 5.0, false, null, 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
    }

    // 7. caffeineTaken=true이지만 lastCaffeineTime=null -> adjustedCaffeineCutoff = null
    @Test
    void 카페인_섭취해도_lastCaffeineTime_없으면_cutoff_null() {
        feedback("2026-08-01", TRANSITION, 5.0, true, null, 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
    }

    // 8. 수면 부족 패턴 2회 -> repeatedPatternFound = true
    @Test
    void 수면부족_2회면_repeatedPatternFound_true() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 5, 4);
        feedback("2026-08-03", TRANSITION, 5.0, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족이 반복되어 추가 수면 시간을 반영합니다.");
    }

    // 9. 높은 피로 패턴 2회 -> repeatedPatternFound = true
    @Test
    void 고피로_2회면_repeatedPatternFound_true() {
        feedback("2026-08-01", TRANSITION, 7.0, false, null, 8, 4);
        feedback("2026-08-03", TRANSITION, 7.0, false, null, 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 높은 피로도가 반복되어 추가 회복 시간을 반영합니다.");
    }

    // 10. 낮은 routineHelpfulness 패턴 2회 -> repeatedPatternFound = true
    @Test
    void 낮은루틴도움_2회면_repeatedPatternFound_true() {
        feedback("2026-08-01", TRANSITION, 7.0, false, null, 5, 2);
        feedback("2026-08-03", TRANSITION, 7.0, false, null, 5, 1);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 기존 회복 루틴의 도움이 낮게 평가되어 회복 루틴 조정을 권장합니다.");
    }

    // 11. 수면 부족 1회 + 낮은 routineHelpfulness 1회처럼 서로 다른 문제만 각각 1회 -> repeatedPatternFound = false
    @Test
    void 서로_다른_패턴이_각각_1회씩이면_repeatedPatternFound_false() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 5, 4);   // 수면 부족 1회
        feedback("2026-08-03", TRANSITION, 7.0, false, null, 5, 2);   // 낮은 루틴 도움 1회

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isFalse();
    }

    // 12. 여러 카페인 보정 대상 Feedback이 있을 경우 가장 최근 feedbackDate의 lastCaffeineTime을 사용
    @Test
    void 카페인_보정_대상이_여러건이면_가장_최근_feedbackDate를_사용() {
        feedback("2026-08-01", TRANSITION, 5.0, true, "13:00", 9, 4); // 더 오래된 기록
        feedback("2026-08-05", TRANSITION, 5.0, true, "16:00", 9, 4); // 가장 최근 기록

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isEqualTo("15:30");
    }

    // 13. 다른 transitionType Feedback이 존재해도 계산에서 제외
    @Test
    void 다른_transitionType_Feedback은_계산에서_제외() {
        feedback("2026-08-01", "DAY_TO_EVENING", 4.0, true, "20:00", 10, 1); // 전부 나쁜 패턴이지만 다른 전환
        feedback("2026-08-02", TRANSITION, 7.0, false, null, 4, 4);          // 실제 대상: 문제 없음

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.isRepeatedPatternFound()).isFalse();
    }

    // 14. adjustedCaffeineCutoff가 반드시 HH:mm 형식인지 확인
    @Test
    void adjustedCaffeineCutoff는_HHmm_형식이다() {
        feedback("2026-08-01", TRANSITION, 5.0, true, "00:10", 9, 4); // 자정 넘어가는 경계 케이스

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).matches("^([01]\\d|2[0-3]):[0-5]\\d$");
        assertThat(res.getAdjustedCaffeineCutoff()).isEqualTo("23:40");
    }

    // 15. Feedback은 존재하지만 수면부족/고피로/카페인보정/낮은루틴도움이 모두 없는 경우
    @Test
    void 문제패턴이_전혀_없으면_기본값과_안내문구() {
        feedback("2026-08-01", TRANSITION, 7.0, true, "10:00", 4, 4); // 카페인 섭취했지만 sleep/fatigue 조건 불만족

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.isRepeatedPatternFound()).isFalse();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("과거 동일 근무 전환 피드백에서 추가 보정이 필요한 반복 패턴은 확인되지 않았습니다.");
    }
}
