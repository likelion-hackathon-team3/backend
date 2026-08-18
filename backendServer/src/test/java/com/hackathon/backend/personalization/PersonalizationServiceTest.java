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
// MVP 확정 규칙: 수면부족/고피로는 60/30 구분 없이 recommendedSleepBuffer=30 하나로 통일하고,
// adjustedCaffeineCutoff는 "수면부족 + 카페인 섭취" 조합에서만 계산한다(고피로만으로는 계산 안 함).
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

    // 0. 동일 transitionType Feedback 없음 -> 기본 응답
    @Test
    void 동일전환_Feedback_없으면_기본응답() {
        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.isRepeatedPatternFound()).isFalse();
        assertThat(res.getRecommendedRoutineNotice()).isEqualTo("축적된 피드백이 없어 기본 추천 기준을 적용합니다.");
    }

    // 1. 수면 부족 5.5h -> recommendedSleepBuffer = 30
    @Test
    void 수면부족_1건이면_30분_보정() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30);
        assertThat(res.isRepeatedPatternFound()).isFalse();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족이 확인되어 추가 수면 시간을 반영합니다.");
    }

    // 2. 고피로 fatigue=8, sleep>=6 -> recommendedSleepBuffer = 30
    @Test
    void 고피로만_1건이어도_30분_보정() {
        feedback("2026-08-01", TRANSITION, 7.0, false, null, 8, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30);
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 높은 피로도가 확인되어 추가 회복 시간을 반영합니다.");
    }

    // 3. 수면 부족 + 고피로 동시 존재 -> recommendedSleepBuffer = 30 (60으로 누적되지 않음)
    @Test
    void 수면부족과_고피로_동시_존재해도_30분만_적용() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30);
    }

    // 4. 정상 수면 + 정상 피로 -> recommendedSleepBuffer = 0
    @Test
    void 정상수면_정상피로면_보정없음() {
        feedback("2026-08-01", TRANSITION, 7.0, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
    }

    // 5. sleep<6 + caffeineTaken=true + lastTime=15:00 -> adjustedCaffeineCutoff = "14:30"
    @Test
    void 수면부족_카페인섭취_동반시_30분_당긴_cutoff() {
        feedback("2026-08-01", TRANSITION, 5.5, true, "15:00", 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isEqualTo("14:30");
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족과 카페인 섭취 기록이 함께 확인되어 카페인 차단 시간을 30분 앞당겨 반영합니다.");
    }

    // 6. sleep>=6 + fatigue>=8 + caffeineTaken=true + lastTime=15:00 -> adjustedCaffeineCutoff = null
    //    (고피로만으로는 카페인 cutoff를 계산하지 않는다)
    @Test
    void 고피로만으로는_카페인cutoff_계산안함() {
        feedback("2026-08-01", TRANSITION, 7.0, true, "15:00", 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30); // 고피로 보정 자체는 여전히 적용됨
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 높은 피로도가 확인되어 추가 회복 시간을 반영합니다.");
    }

    // 7. caffeineTaken=false -> adjustedCaffeineCutoff = null
    @Test
    void 카페인_미섭취면_cutoff_null() {
        feedback("2026-08-01", TRANSITION, 5.0, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
    }

    // 8. lastCaffeineTime=null -> adjustedCaffeineCutoff = null
    @Test
    void lastCaffeineTime_없으면_cutoff_null() {
        feedback("2026-08-01", TRANSITION, 5.0, true, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
    }

    // 9. 수면 부족 2회 -> repeatedPatternFound = true
    @Test
    void 수면부족_2회면_repeatedPatternFound_true() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 5, 4);
        feedback("2026-08-03", TRANSITION, 5.0, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족이 반복되어 추가 수면 시간을 반영합니다.");
    }

    // 10. 높은 피로 2회 -> repeatedPatternFound = true
    @Test
    void 고피로_2회면_repeatedPatternFound_true() {
        feedback("2026-08-01", TRANSITION, 7.0, false, null, 8, 4);
        feedback("2026-08-03", TRANSITION, 7.0, false, null, 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 높은 피로도가 반복되어 추가 회복 시간을 반영합니다.");
    }

    // 11. 카페인 보정 대상 2회 -> repeatedPatternFound = true
    @Test
    void 카페인보정대상_2회면_repeatedPatternFound_true() {
        feedback("2026-08-01", TRANSITION, 5.5, true, "13:00", 5, 4);
        feedback("2026-08-03", TRANSITION, 5.0, true, "16:00", 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족과 카페인 섭취 기록이 반복적으로 함께 확인되어 카페인 차단 시간을 30분 앞당겨 반영합니다.");
    }

    // 12. 높은 피로만 2회 + 카페인 기록 존재하더라도 sleep>=6이면 카페인 보정 반복 패턴으로 세지 않는다.
    @Test
    void 고피로만_2회에_카페인기록_있어도_수면정상이면_카페인패턴_아님() {
        feedback("2026-08-01", TRANSITION, 7.0, true, "15:00", 8, 4);
        feedback("2026-08-03", TRANSITION, 7.0, true, "16:00", 9, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        // 고피로 패턴 자체는 2회라 repeatedPatternFound=true지만, cutoff는 여전히 null이어야 한다.
        assertThat(res.isRepeatedPatternFound()).isTrue();
        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 높은 피로도가 반복되어 추가 회복 시간을 반영합니다.");
    }

    // 13. 낮은 routineHelpfulness 패턴 2회 -> repeatedPatternFound = true
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

    // 14. 서로 다른 문제가 각각 1회씩만 존재 -> repeatedPatternFound = false
    @Test
    void 서로_다른_패턴이_각각_1회씩이면_repeatedPatternFound_false() {
        feedback("2026-08-01", TRANSITION, 5.5, false, null, 5, 4);   // 수면 부족 1회
        feedback("2026-08-03", TRANSITION, 7.0, false, null, 5, 2);   // 낮은 루틴 도움 1회

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.isRepeatedPatternFound()).isFalse();
    }

    // 15. 여러 카페인 보정 대상 Feedback이 있으면 가장 최근 feedbackDate 기준 lastCaffeineTime을 사용
    @Test
    void 카페인_보정_대상이_여러건이면_가장_최근_feedbackDate를_사용() {
        feedback("2026-08-01", TRANSITION, 5.0, true, "13:00", 5, 4); // 더 오래된 기록
        feedback("2026-08-05", TRANSITION, 5.0, true, "16:00", 5, 4); // 가장 최근 기록

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).isEqualTo("15:30");
    }

    // 16. 다른 transitionType Feedback이 존재해도 계산에서 제외
    @Test
    void 다른_transitionType_Feedback은_계산에서_제외() {
        feedback("2026-08-01", "DAY_TO_EVENING", 4.0, true, "20:00", 10, 1); // 전부 나쁜 패턴이지만 다른 전환
        feedback("2026-08-02", TRANSITION, 7.0, false, null, 4, 4);          // 실제 대상: 문제 없음

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.isRepeatedPatternFound()).isFalse();
    }

    // 17. adjustedCaffeineCutoff가 반드시 HH:mm 형식이고, 자정 경계도 올바르게 처리한다.
    @Test
    void adjustedCaffeineCutoff는_HHmm_형식이고_자정_경계를_넘긴다() {
        feedback("2026-08-01", TRANSITION, 5.0, true, "00:10", 5, 4); // 자정 넘어가는 경계 케이스

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getAdjustedCaffeineCutoff()).matches("^([01]\\d|2[0-3]):[0-5]\\d$");
        assertThat(res.getAdjustedCaffeineCutoff()).isEqualTo("23:40");
    }

    // 18. Feedback은 존재하지만 수면부족/고피로/카페인보정/낮은루틴도움이 모두 없는 경우
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

    // ===== getPersonalization(shiftType, targetDate) — Timeline 전용 날짜 경계 검증 =====
    // targetDate보다 "엄격히 이전(<)"인 Feedback만 사용해야 한다(자기 자신/미래 Feedback 인과관계 문제 방지).

    // 19. targetDate 이전 Feedback은 포함된다
    @Test
    void targetDate_이전_Feedback은_포함된다() {
        feedback("2026-08-05", TRANSITION, 5.5, false, null, 5, 4); // targetDate(08-10)보다 이전

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION, LocalDate.parse("2026-08-10"));

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30);
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 수면 부족이 확인되어 추가 수면 시간을 반영합니다.");
    }

    // 20. targetDate와 같은 날짜 Feedback은 제외된다
    @Test
    void targetDate와_같은_날짜_Feedback은_제외된다() {
        LocalDate targetDate = LocalDate.parse("2026-08-10");
        feedback("2026-08-10", TRANSITION, 5.5, false, null, 5, 4); // targetDate 당일

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION, targetDate);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getRecommendedRoutineNotice()).isEqualTo("축적된 피드백이 없어 기본 추천 기준을 적용합니다.");
    }

    // 21. targetDate 이후 Feedback은 제외된다
    @Test
    void targetDate_이후_Feedback은_제외된다() {
        feedback("2026-08-15", TRANSITION, 5.5, false, null, 5, 4); // targetDate(08-10)보다 이후

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION, LocalDate.parse("2026-08-10"));

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getRecommendedRoutineNotice()).isEqualTo("축적된 피드백이 없어 기본 추천 기준을 적용합니다.");
    }

    // 22. 이전/같은날/이후가 섞여 있어도 이전 데이터만 계산에 사용된다.
    // 같은날/이후 기록에만 있는 문제(수면부족/고피로)가 결과에 새어 들어오면 이 테스트가 잡아낸다.
    @Test
    void 이전_같은날_이후가_섞여도_이전_데이터만_계산에_사용된다() {
        LocalDate targetDate = LocalDate.parse("2026-08-10");
        feedback("2026-08-05", TRANSITION, 7.0, false, null, 4, 1); // 이전: 낮은 루틴 도움만 있음
        feedback("2026-08-10", TRANSITION, 5.0, false, null, 4, 4); // 같은날: 수면부족(제외돼야 함)
        feedback("2026-08-15", TRANSITION, 7.0, false, null, 9, 4); // 이후: 고피로(제외돼야 함)

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION, targetDate);

        // 같은날/이후의 수면부족·고피로가 섞였다면 sleepBuffer=30이 됐을 것이다.
        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getAdjustedCaffeineCutoff()).isNull();
        assertThat(res.isRepeatedPatternFound()).isFalse();
        assertThat(res.getRecommendedRoutineNotice())
                .isEqualTo("지난 동일 근무 전환에서 기존 회복 루틴의 도움이 낮게 평가되어 회복 루틴 조정을 권장합니다.");
    }

    // 23. 기존 1인자 getPersonalization(shiftType)은 날짜 제한 없이 전체 동일 transitionType 기준으로
    //     그대로 동작한다(22번과 동일한 데이터로, 날짜 제한이 없으면 같은날/이후 수면부족까지 잡혀야 함).
    @Test
    void 기존_1인자_조회는_날짜_제한_없이_전체를_그대로_사용한다() {
        feedback("2026-08-05", TRANSITION, 7.0, false, null, 4, 1);
        feedback("2026-08-10", TRANSITION, 5.0, false, null, 4, 4); // 수면부족
        feedback("2026-08-15", TRANSITION, 7.0, false, null, 9, 4); // 고피로

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(30);
    }

    // 24. targetDate가 null이면 예외 없이 방어적으로 기본 응답을 반환한다.
    @Test
    void targetDate가_null이면_방어적으로_기본응답() {
        feedback("2026-08-05", TRANSITION, 5.5, false, null, 5, 4);

        PersonalizationResponse res = personalizationService.getPersonalization(TRANSITION, null);

        assertThat(res.getRecommendedSleepBuffer()).isEqualTo(0);
        assertThat(res.getRecommendedRoutineNotice()).isEqualTo("축적된 피드백이 없어 기본 추천 기준을 적용합니다.");
    }
}
