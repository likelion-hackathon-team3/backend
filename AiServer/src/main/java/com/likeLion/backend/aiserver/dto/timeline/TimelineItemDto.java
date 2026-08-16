package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "타임라인 개별 일정 아이템 DTO")
public record TimelineItemDto(
        @Schema(description = "시작/해당 시각 (HH:mm)", example = "00:40")
        String time,

        @Schema(description = "일정 제목", example = "취침 (권장 취침 시간)")
        String title,

        @Schema(description = "상세 설명", example = "수면 목표 5시간 10분")
        String description,

        @Schema(description = "활동 카테고리 (MEAL, PREPARATION, SLEEP, WAKE_UP, WORK, NAP, REST, EXERCISE, FREE)", example = "SLEEP")
        ActivityType category,

        @Schema(description = "강조 텍스트 (옵셔널)", example = "권장 수면 시간: 5시간 10분")
        String highlight
) {
}
