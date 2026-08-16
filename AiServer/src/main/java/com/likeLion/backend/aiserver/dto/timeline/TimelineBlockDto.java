package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "타임라인 개별 활동 블록 DTO")
public record TimelineBlockDto(
        @Schema(description = "시작 시각 (HH:mm)", example = "08:00")
        String startTime,

        @Schema(description = "종료 시각 (HH:mm)", example = "10:30")
        String endTime,

        @Schema(description = "활동 유형 (SLEEP, NAP, MEAL, WORK, REST, EXERCISE, FREE)", example = "SLEEP")
        ActivityType activityType,

        @Schema(description = "일정 제목", example = "암막 커튼 치고 숙면 취하기")
        String title,

        @Schema(description = "상세 가이드 및 팁", example = "NIGHT 근무 전 최소 2시간 이상의 깊은 수면을 확보하세요.")
        String description
) {
}
