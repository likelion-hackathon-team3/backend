package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "규칙 엔진이 산출한 기본 고정 슬롯 DTO")
public record BaseSlotDto(
        @Schema(description = "시작 일시 (ISO-8601)", example = "2026-08-20T23:30")
        String time,

        @Schema(description = "활동 카테고리", example = "SLEEP")
        ActivityType category,

        @Schema(description = "예정 소요 시간(분 단위)", example = "420")
        Long durationMinutes,

        @Schema(description = "기본 제목 힌트", example = "취침")
        String defaultTitle,

        @Schema(description = "강조 텍스트 힌트 (옵셔널)", example = "권장 수면: 7시간")
        String highlight
) {
}
