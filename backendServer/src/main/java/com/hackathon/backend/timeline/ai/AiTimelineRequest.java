package com.hackathon.backend.timeline.ai;

// Spring -> AiServer POST /api/timeline/generate 요청 본문.
// 팀에서 확정한 계약 그대로: 필드는 해당 사항이 없어도 생략하지 않고 null로 채운다
// (예: currentShift==OFF -> currentWorkEnd=null, FUTURE -> currentTime/analysisResult=null).
// NIGHT roster date 등 실제 LocalDateTime 계산은 전부 호출하는 쪽(ShiftDateTimeResolver 재사용)에서
// 끝내고, 이 DTO는 이미 확정된 문자열/값만 담는다.
public record AiTimelineRequest(
        String targetDate,
        String currentTime,
        String currentShift,
        String nextShift,
        String transitionType,
        String currentWorkEnd,
        String nextWorkStart,
        Integer commuteMinutes,
        ShiftTimes shiftTimes,
        AnalysisResult analysisResult,
        String userNotes,
        PersonalizationRequest personalization
) {
    public record ShiftTimes(
            String dayStart,
            String dayEnd,
            String eveningStart,
            String eveningEnd,
            String nightStart,
            String nightEnd
    ) {
    }

    public record AnalysisResult(
            String riskLevel,
            String recoveryStatus,
            String fatigueLevel,
            Double availableHours,
            Integer consecutiveDays
    ) {
    }

    // AiServer PersonalizationDto와 같은 모양(recommendedSleepBuffer/adjustedCaffeineCutoff)의 요청 측 DTO.
    // PersonalizationResponse(Personalization 패키지)를 여기서 직접 참조하지 않고, 호출하는 쪽
    // (AiTimelineRequestBuilder)이 이미 꺼낸 두 값만 받는다.
    public record PersonalizationRequest(
            Integer recommendedSleepBuffer,
            String adjustedCaffeineCutoff
    ) {
    }
}
