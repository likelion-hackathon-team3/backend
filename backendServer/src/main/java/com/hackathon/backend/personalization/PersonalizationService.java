package com.hackathon.backend.personalization;

import com.hackathon.backend.feedback.Feedback;
import com.hackathon.backend.feedback.FeedbackRepository;
import com.hackathon.backend.personalization.dto.PersonalizationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// GET /api/personalization 실제 로직을 담는 계층.
// 새 Entity/Table 없이 기존 FeedbackRepository만으로 동일 transitionType(=shiftType) 과거 Feedback을
// 조회해서 요청 시점에 계산한다(Dashboard와 동일한 접근 방식).
// AI 판단이나 가중치 계산 없이 deterministic rule만 사용한다.
//
// 이 API 응답 계약에는 success/message 필드가 없다(명세 확정). 그래서 shiftType이 없거나 공백이거나
// 동일 transitionType Feedback이 0개인 경우 전부 "축적된 피드백 없음" 기본 응답으로 처리하고,
// 별도의 실패 응답 타입을 새로 만들지 않는다.
@Service
public class PersonalizationService {

    private static final double SLEEP_SHORTAGE_HOURS = 6.0;
    private static final int HIGH_FATIGUE_SCORE = 8;
    private static final int LOW_ROUTINE_HELPFULNESS = 2;

    // MVP 단순화(팀 확정): 수면부족/고피로를 60/30으로 구분하지 않고, 개인화 수면·회복 보정은
    // 둘 중 하나라도 있으면 30분 하나로 통일한다.
    private static final int SLEEP_OR_FATIGUE_BUFFER_MINUTES = 30;
    private static final int CAFFEINE_CUTOFF_SHIFT_MINUTES = 30;

    private static final DateTimeFormatter CUTOFF_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final FeedbackRepository feedbackRepository;

    public PersonalizationService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public PersonalizationResponse getPersonalization(String shiftType) {
        if (shiftType == null || shiftType.isBlank()) {
            return PersonalizationResponse.noAccumulatedFeedback();
        }

        // 최신 날짜부터 내림차순 — 카페인 cutoff 후보를 찾을 때 "가장 최근 Feedback"을
        // 별도 정렬 없이 그대로 첫 번째 매치로 얻기 위함.
        List<Feedback> pastFeedback = feedbackRepository.findByTransitionTypeOrderByFeedbackDateDesc(shiftType);
        if (pastFeedback.isEmpty()) {
            return PersonalizationResponse.noAccumulatedFeedback();
        }

        long sleepShortageCount = pastFeedback.stream().filter(this::isSleepShortage).count();
        long highFatigueCount = pastFeedback.stream().filter(this::isHighFatigue).count();
        long caffeineAdjustmentCount = pastFeedback.stream().filter(this::isCaffeineAdjustmentCandidate).count();
        long lowRoutineCount = pastFeedback.stream().filter(this::isLowRoutineHelpfulness).count();

        boolean sleepShortage = sleepShortageCount > 0;
        boolean highFatigue = highFatigueCount > 0;

        // MVP 규칙(확정): 수면부족 또는 고피로 중 하나라도 있으면 30분, 없으면 0분. 누적/구분하지 않는다.
        int recommendedSleepBuffer = (sleepShortage || highFatigue) ? SLEEP_OR_FATIGUE_BUFFER_MINUTES : 0;

        // 조건(caffeineTaken && lastCaffeineTime 존재 && 수면부족)을 만족하는 가장 최근 Feedback 하나.
        // 고피로만으로는 카페인 cutoff를 보정하지 않는다(팀 확정). 리스트가 이미 feedbackDate
        // 내림차순이라 findFirst가 곧 최신값이다.
        Optional<Feedback> caffeineCandidate = pastFeedback.stream()
                .filter(this::isCaffeineAdjustmentCandidate)
                .findFirst();
        String adjustedCaffeineCutoff = caffeineCandidate
                .map(f -> f.getLastCaffeineTime().minusMinutes(CAFFEINE_CUTOFF_SHIFT_MINUTES).format(CUTOFF_TIME_FORMAT))
                .orElse(null);

        boolean repeatedPatternFound = sleepShortageCount >= 2 || highFatigueCount >= 2
                || caffeineAdjustmentCount >= 2 || lowRoutineCount >= 2;

        String recommendedRoutineNotice = buildNotice(sleepShortage, highFatigue,
                adjustedCaffeineCutoff != null, lowRoutineCount > 0,
                sleepShortageCount, highFatigueCount, caffeineAdjustmentCount);

        return new PersonalizationResponse(adjustedCaffeineCutoff, recommendedSleepBuffer,
                repeatedPatternFound, recommendedRoutineNotice);
    }

    private boolean isSleepShortage(Feedback feedback) {
        return feedback.getActualSleepDuration() < SLEEP_SHORTAGE_HOURS;
    }

    private boolean isHighFatigue(Feedback feedback) {
        return feedback.getPostShiftFatigue() >= HIGH_FATIGUE_SCORE;
    }

    private boolean isLowRoutineHelpfulness(Feedback feedback) {
        return feedback.getRoutineHelpfulness() <= LOW_ROUTINE_HELPFULNESS;
    }

    // 카페인 cutoff 보정 대상(=repeatedPatternFound의 카페인 패턴)은 수면부족일 때만 성립한다.
    // 고피로 조건은 여기서 제거됐다(팀 확정) — "카페인 때문에 피로가 높아졌다"는 인과관계를
    // 암시하지 않기 위해, 실제로 수면 부족과 카페인 섭취가 함께 관찰된 경우로만 좁힌다.
    private boolean isCaffeineAdjustmentCandidate(Feedback feedback) {
        return feedback.isCaffeineTaken()
                && feedback.getLastCaffeineTime() != null
                && isSleepShortage(feedback);
    }

    // 문구 우선순위: [1]수면부족+카페인cutoff 동시 > [2]수면부족만 > [3]고피로만 > [4]낮은루틴도움만 > [5]패턴없음.
    // "확인/반복" 표현은 해당 패턴의 실제 발생 횟수(count>=2)로 판단한다.
    //
    // 주의: isCaffeineAdjustmentCandidate가 수면부족을 요구하므로 caffeineApplied==true이면
    // sleepShortage도 항상 true다(카페인 cutoff는 수면부족의 부분집합). 그래서 caffeineApplied를
    // sleepShortage보다 먼저 검사해야 한다 — 순서가 바뀌면 카페인 문구 분기가 절대 도달하지 않는다.
    private String buildNotice(boolean sleepShortage, boolean highFatigue, boolean caffeineApplied,
                                boolean lowRoutinePresent, long sleepShortageCount, long highFatigueCount,
                                long caffeineAdjustmentCount) {
        if (caffeineApplied) {
            return caffeineAdjustmentCount >= 2
                    ? "지난 동일 근무 전환에서 수면 부족과 카페인 섭취 기록이 반복적으로 함께 확인되어 카페인 차단 시간을 30분 앞당겨 반영합니다."
                    : "지난 동일 근무 전환에서 수면 부족과 카페인 섭취 기록이 함께 확인되어 카페인 차단 시간을 30분 앞당겨 반영합니다.";
        }
        if (sleepShortage) {
            return sleepShortageCount >= 2
                    ? "지난 동일 근무 전환에서 수면 부족이 반복되어 추가 수면 시간을 반영합니다."
                    : "지난 동일 근무 전환에서 수면 부족이 확인되어 추가 수면 시간을 반영합니다.";
        }
        if (highFatigue) {
            return highFatigueCount >= 2
                    ? "지난 동일 근무 전환에서 높은 피로도가 반복되어 추가 회복 시간을 반영합니다."
                    : "지난 동일 근무 전환에서 높은 피로도가 확인되어 추가 회복 시간을 반영합니다.";
        }
        if (lowRoutinePresent) {
            return "지난 동일 근무 전환에서 기존 회복 루틴의 도움이 낮게 평가되어 회복 루틴 조정을 권장합니다.";
        }
        return "과거 동일 근무 전환 피드백에서 추가 보정이 필요한 반복 패턴은 확인되지 않았습니다.";
    }
}
