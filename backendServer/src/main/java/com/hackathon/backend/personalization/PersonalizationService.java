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

    private static final int SLEEP_SHORTAGE_BUFFER_MINUTES = 60;
    private static final int HIGH_FATIGUE_BUFFER_MINUTES = 30;
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

        // 우선순위: 수면 부족 -> 60분 / 높은 피로 -> 30분 / 그 외 -> 0분. 누적하지 않는다.
        int recommendedSleepBuffer = sleepShortage ? SLEEP_SHORTAGE_BUFFER_MINUTES
                : highFatigue ? HIGH_FATIGUE_BUFFER_MINUTES : 0;

        // 조건(caffeineTaken && lastCaffeineTime 존재 && (수면부족 또는 고피로))을 만족하는
        // 가장 최근 Feedback 하나. 리스트가 이미 feedbackDate 내림차순이라 findFirst가 곧 최신값이다.
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

    private boolean isCaffeineAdjustmentCandidate(Feedback feedback) {
        return feedback.isCaffeineTaken()
                && feedback.getLastCaffeineTime() != null
                && (isSleepShortage(feedback) || isHighFatigue(feedback));
    }

    // 문구 우선순위: [1]수면부족 > [2]카페인cutoff(고피로 동반) > [3]고피로만 > [4]낮은루틴도움만 > [5]패턴없음.
    // "확인/반복" 표현은 해당 패턴의 실제 발생 횟수(count>=2)로 판단한다.
    private String buildNotice(boolean sleepShortage, boolean highFatigue, boolean caffeineApplied,
                                boolean lowRoutinePresent, long sleepShortageCount, long highFatigueCount,
                                long caffeineAdjustmentCount) {
        if (sleepShortage) {
            return sleepShortageCount >= 2
                    ? "지난 동일 근무 전환에서 수면 부족이 반복되어 추가 수면 시간을 반영합니다."
                    : "지난 동일 근무 전환에서 수면 부족이 확인되어 추가 수면 시간을 반영합니다.";
        }
        if (caffeineApplied) {
            return caffeineAdjustmentCount >= 2
                    ? "지난 동일 근무 전환에서 높은 피로도와 카페인 섭취 기록이 반복적으로 함께 확인되어 카페인 차단 시간을 30분 앞당겨 반영합니다."
                    : "지난 동일 근무 전환에서 높은 피로도와 카페인 섭취 기록이 함께 확인되어 카페인 차단 시간을 30분 앞당겨 반영합니다.";
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
