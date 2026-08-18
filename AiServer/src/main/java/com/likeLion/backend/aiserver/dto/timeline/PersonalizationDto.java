package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "과거 근무 이력 및 피드백 기반 개인화 보정 제약조건 DTO")
public record PersonalizationDto(
        @Schema(description = "추가 권장 수면 보정 시간 (분 단위, 예: 30이면 기본 수면에 최대 30분 탄력 추가 확보)", example = "30")
        Integer recommendedSleepBuffer,

        @Schema(description = "카페인 섭취 중단 권장 시각 (HH:mm 형식, 예: 14:30)", example = "14:30")
        String adjustedCaffeineCutoff
) {
    public PersonalizationDto(
            Integer recommendedSleepBuffer,
            String adjustedCaffeineCutoff,
            Boolean hasRepeatedPattern,
            String personalizationMessage
    ) {
        this(recommendedSleepBuffer, adjustedCaffeineCutoff);
    }

    public int sleepBufferOrDefault() {
        return recommendedSleepBuffer != null ? recommendedSleepBuffer : 0;
    }

    public String caffeineCutoffOrDefault() {
        return (adjustedCaffeineCutoff != null && !adjustedCaffeineCutoff.isBlank()) ? adjustedCaffeineCutoff : "해당 없음";
    }
}
