package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "병원/사용자 맞춤 근무 시간대 설정 DTO")
public record ShiftTimesDto(
        @Schema(description = "DAY 근무 시간대", example = "07:00 ~ 15:00")
        String dayTime,

        @Schema(description = "EVENING 근무 시간대", example = "15:00 ~ 23:00")
        String eveningTime,

        @Schema(description = "NIGHT 근무 시간대", example = "23:00 ~ 익일 07:00")
        String nightTime
) {
    public static final String DEFAULT_DAY_TIME = "07:00 ~ 15:00";
    public static final String DEFAULT_EVENING_TIME = "15:00 ~ 23:00";
    public static final String DEFAULT_NIGHT_TIME = "23:00 ~ 익일 07:00";

    public String dayTimeOrDefault() {
        return (dayTime != null && !dayTime.isBlank()) ? dayTime : DEFAULT_DAY_TIME;
    }

    public String eveningTimeOrDefault() {
        return (eveningTime != null && !eveningTime.isBlank()) ? eveningTime : DEFAULT_EVENING_TIME;
    }

    public String nightTimeOrDefault() {
        return (nightTime != null && !nightTime.isBlank()) ? nightTime : DEFAULT_NIGHT_TIME;
    }
}
