package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "규칙 엔진이 산출한 타임라인 뼈대 및 여유 구간 DTO")
public record TimelineSkeletonDto(
        @Schema(description = "고정 필수 활동 슬롯 리스트")
        List<BaseSlotDto> baseSlots,

        @Schema(description = "AI가 자율적으로 채울 수 있는 여유 시간 구간 안내 문자열", example = "2026-08-21T12:30 ~ 2026-08-21T16:30 (4시간 여유)")
        List<String> flexIntervals,

        @Schema(description = "총 잔여 자유 시간 문자열", example = "32시간 00분")
        String totalFreeTimeFormatted
) {
}
