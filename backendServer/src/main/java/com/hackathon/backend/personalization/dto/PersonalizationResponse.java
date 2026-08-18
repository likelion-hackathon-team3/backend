package com.hackathon.backend.personalization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// GET /api/personalization 응답. success/message 래퍼 없이 계산 결과 4개 필드만 그대로 반환한다.
// { "adjustedCaffeineCutoff", "recommendedSleepBuffer", "repeatedPatternFound", "recommendedRoutineNotice" }
@Getter
@AllArgsConstructor
public class PersonalizationResponse {

    private String adjustedCaffeineCutoff; // "HH:mm" 또는 보정 대상 없으면 null
    private int recommendedSleepBuffer;    // 분 단위
    private boolean repeatedPatternFound;
    private String recommendedRoutineNotice;

    // 동일 transitionType의 과거 Feedback이 하나도 없을 때 반환하는 기본 응답.
    // Service가 직접 null/blank 값으로 호출되는 경우에도 방어적으로 동일 응답을 반환한다.
    public static PersonalizationResponse noAccumulatedFeedback() {
        return new PersonalizationResponse(null, 0, false,
                "축적된 피드백이 없어 기본 추천 기준을 적용합니다.");
    }
}
