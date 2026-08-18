package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "과거 근무 이력 및 피드백 기반 개인화 보정 지표 DTO")
public record PersonalizationDto(
        @Schema(description = "추가 권장 수면 보정 시간 (분 단위, 예: 30이면 기본 수면에 30분 추가 확보)", example = "30")
        Integer recommendedSleepBuffer,

        @Schema(description = "카페인 섭취 중단 권장 시각 (HH:mm 형식, 예: 14:30)", example = "14:30")
        String adjustedCaffeineCutoff,

        @Schema(description = "동일 근무 전환 패턴에서의 피로 누적/취약 반복 감지 여부", example = "true")
        Boolean hasRepeatedPattern,

        @Schema(description = "개인화 안내 및 피드백 요약 문구", example = "지난 동일 근무 전환 시 피로도가 높았어요. 오늘은 수면 30분을 더 확보해보세요.")
        String personalizationMessage
) {
    public int sleepBufferOrDefault() {
        return recommendedSleepBuffer != null ? recommendedSleepBuffer : 0;
    }

    public String caffeineCutoffOrDefault() {
        return (adjustedCaffeineCutoff != null && !adjustedCaffeineCutoff.isBlank()) ? adjustedCaffeineCutoff : "해당 없음";
    }

    public String messageOrDefault() {
        return (personalizationMessage != null && !personalizationMessage.isBlank()) ? personalizationMessage : "없음";
    }
}
