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

        @Schema(description = "실시간 통합 분석 결과 (당일 모드일 때만 포함, 미래 모드 시 null)")
        AnalysisResultDto analysisResult
) {
}
