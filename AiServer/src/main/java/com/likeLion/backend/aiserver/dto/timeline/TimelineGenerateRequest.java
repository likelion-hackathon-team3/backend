package com.likeLion.backend.aiserver.dto.timeline;

import com.likeLion.backend.aiserver.dto.ShiftType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "AI 추천 타임라인 생성 요청 DTO")
public record TimelineGenerateRequest(
        @Schema(description = "대상 날짜 (YYYY-MM-DD)", example = "2026-08-17")
        LocalDate targetDate,

        @Schema(description = "현재/기준일 근무 유형", example = "DAY")
        ShiftType currentShift,

        @Schema(description = "다음 근무 유형", example = "NIGHT")
        ShiftType nextShift,

        @Schema(description = "근무 전환 유형 (예: DAY_TO_NIGHT, EVENING_TO_DAY)", example = "DAY_TO_NIGHT")
        String transitionType,

        @Schema(description = "호출 시점의 현재 시각 (HH:mm, 당일 모드 시 이 시각 이후의 잔여 일정 위주로 생성)", example = "16:30")
        String currentTime,

        @Schema(description = "병원/사용자 맞춤 근무 시간대 설정 (선택적, 미입력 시 표준 시간 기준 적용)")
        ShiftTimesDto shiftTimes,

        @Schema(description = "실시간 통합 분석 결과 (당일 모드일 때만 포함, 미래 모드 시 null)")
        AnalysisResultDto analysisResult
) {
    public TimelineGenerateRequest(
            LocalDate targetDate,
            ShiftType currentShift,
            ShiftType nextShift,
            String transitionType,
            AnalysisResultDto analysisResult
    ) {
        this(targetDate, currentShift, nextShift, transitionType, null, null, analysisResult);
    }

    public TimelineGenerateRequest(
            LocalDate targetDate,
            ShiftType currentShift,
            ShiftType nextShift,
            String transitionType,
            ShiftTimesDto shiftTimes,
            AnalysisResultDto analysisResult
    ) {
        this(targetDate, currentShift, nextShift, transitionType, null, shiftTimes, analysisResult);
    }
}
