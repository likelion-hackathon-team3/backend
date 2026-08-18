package com.hackathon.backend.timeline.ai;

import com.hackathon.backend.analysis.ShiftDateTimeResolver;
import com.hackathon.backend.analysis.dto.AnalysisResponse;
import com.hackathon.backend.analysis.dto.CurrentConditionResponse;
import com.hackathon.backend.dailystatus.FatigueLevel;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// currentShift/nextShift/Environment/now(단일 캡처)를 이용해
// AiServer POST /api/timeline/generate 요청 본문을 조립한다.
// TimelineRange는 timeline 패키지 내부 전용(package-private)이라 여기서 직접 참조하지 않고,
// 호출하는 쪽(TimelineService)이 range.nextWorkActualStart()만 뽑아서 넘겨준다.
// LocalDate.now()/LocalTime.now()/LocalDateTime.now()를 여기서 직접 호출하지 않는다
// (자정 경계 불일치를 막기 위해 TimelineService가 캡처한 now를 그대로 받는다).
//
// analysisResponse 호출 규약:
// - PAST: 이 build()를 아예 호출하지 않는다.
// - TODAY + Analysis 확보 실패(success=false/예외): 이 build()를 아예 호출하지 않고 fallback으로 내려간다.
// - TODAY + Analysis 확보 성공: analysisResponse에 non-null AnalysisResponse를 전달한다.
// - FUTURE: analysisResponse=null을 전달한다.
// isToday && analysisResponse != null일 때만 실제로 analysisResult를 채워서, 호출부 실수로
// FUTURE에 AnalysisResponse가 잘못 전달되더라도 요청에는 포함되지 않도록 한 번 더 방어한다.
//
// recommendedSleepBuffer/adjustedCaffeineCutoff: PersonalizationResponse(personalization 패키지)를
// 여기서 직접 참조하지 않는다(이 클래스는 순수 조립기로 유지). 호출하는 쪽(TimelineService)이
// PersonalizationService를 호출해서 이미 꺼내둔 값 두 개만 그대로 받아 전달한다.
public class AiTimelineRequestBuilder {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ShiftDateTimeResolver shiftDateTimeResolver = new ShiftDateTimeResolver();

    public AiTimelineRequest build(LocalDate date, ShiftType currentShift, ShiftType nextShift,
                                   LocalDateTime nextWorkActualStart, Environment environment,
                                   boolean isToday, LocalDateTime now,
                                   AnalysisResponse analysisResponse,
                                   int recommendedSleepBuffer, String adjustedCaffeineCutoff) {
        String currentWorkEnd = currentShift == ShiftType.OFF
                ? null
                : shiftDateTimeResolver.actualEnd(date, currentShift, environment).format(DATE_TIME_FORMAT);
        String nextWorkStart = nextShift == ShiftType.OFF
                ? null
                : nextWorkActualStart.format(DATE_TIME_FORMAT);

        return new AiTimelineRequest(
                date.toString(),
                isToday ? now.toLocalTime().format(TIME_FORMAT) : null,
                currentShift.name(),
                nextShift.name(),
                currentShift.name() + "_TO_" + nextShift.name(),
                currentWorkEnd,
                nextWorkStart,
                environment.getCommuteMinutes(),
                buildShiftTimes(environment),
                isToday && analysisResponse != null ? toAnalysisResult(analysisResponse) : null,
                null,
                new AiTimelineRequest.PersonalizationRequest(recommendedSleepBuffer, adjustedCaffeineCutoff)
        );
    }

    private AiTimelineRequest.ShiftTimes buildShiftTimes(Environment environment) {
        return new AiTimelineRequest.ShiftTimes(
                environment.getDayStart().format(TIME_FORMAT),
                environment.getDayEnd().format(TIME_FORMAT),
                environment.getEveningStart().format(TIME_FORMAT),
                environment.getEveningEnd().format(TIME_FORMAT),
                environment.getNightStart().format(TIME_FORMAT),
                environment.getNightEnd().format(TIME_FORMAT)
        );
    }

    // 기존 AnalysisResponse(한글 fatigueLevel/recoveryStatus)를 AiServer 계약(영문 토큰)으로 변환한다.
    // Analysis 패키지 파일은 건드리지 않고, 이 변환만 여기(timeline.ai)에 둔다.
    private AiTimelineRequest.AnalysisResult toAnalysisResult(AnalysisResponse analysisResponse) {
        CurrentConditionResponse condition = analysisResponse.getCurrentCondition();
        return new AiTimelineRequest.AnalysisResult(
                analysisResponse.getRiskLevel(),
                toRecoveryStatusToken(condition.getRecoveryStatus()),
                FatigueLevel.fromKorean(condition.getFatigueLevel()).name(),
                analysisResponse.getAvailableHours(),
                analysisResponse.getConsecutiveDays()
        );
    }

    // CurrentConditionResponse.koreanOf(RecoveryStatus)의 역방향 매핑.
    // RecoveryStatus에는 fromKorean이 없어 Analysis 패키지를 건드리지 않고 여기서만 변환한다.
    private String toRecoveryStatusToken(String korean) {
        return switch (korean) {
            case "양호" -> "GOOD";
            case "회복 필요" -> "RECOVERY_NEEDED";
            case "회복 우선 필요" -> "RECOVERY_PRIORITY";
            default -> throw new IllegalStateException("알 수 없는 recoveryStatus 한글 값: " + korean);
        };
    }
}
