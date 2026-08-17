package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "병원/사용자 맞춤 근무 시간대 설정 DTO")
public record ShiftTimesDto(
        @Schema(description = "DAY 근무 시작 시각 (HH:mm)", example = "07:00")
        String dayStart,

        @Schema(description = "DAY 근무 종료 시각 (HH:mm)", example = "15:00")
        String dayEnd,

        @Schema(description = "EVENING 근무 시작 시각 (HH:mm)", example = "15:00")
        String eveningStart,

        @Schema(description = "EVENING 근무 종료 시각 (HH:mm)", example = "23:00")
        String eveningEnd,

        @Schema(description = "NIGHT 근무 시작 시각 (HH:mm)", example = "23:00")
        String nightStart,

        @Schema(description = "NIGHT 근무 종료 시각 (HH:mm)", example = "07:00")
        String nightEnd
) {
    public static final String DEFAULT_DAY_START = "07:00";
    public static final String DEFAULT_DAY_END = "15:00";
    public static final String DEFAULT_EVENING_START = "15:00";
    public static final String DEFAULT_EVENING_END = "23:00";
    public static final String DEFAULT_NIGHT_START = "23:00";
    public static final String DEFAULT_NIGHT_END = "07:00";

    public static final String DEFAULT_DAY_TIME = DEFAULT_DAY_START + " ~ " + DEFAULT_DAY_END;
    public static final String DEFAULT_EVENING_TIME = DEFAULT_EVENING_START + " ~ " + DEFAULT_EVENING_END;
    public static final String DEFAULT_NIGHT_TIME = DEFAULT_NIGHT_START + " ~ 익일 " + DEFAULT_NIGHT_END;

    public String dayTimeOrDefault() {
        String start = (dayStart != null && !dayStart.isBlank()) ? dayStart : DEFAULT_DAY_START;
        String end = (dayEnd != null && !dayEnd.isBlank()) ? dayEnd : DEFAULT_DAY_END;
        return start + " ~ " + end;
    }

    public String eveningTimeOrDefault() {
        String start = (eveningStart != null && !eveningStart.isBlank()) ? eveningStart : DEFAULT_EVENING_START;
        String end = (eveningEnd != null && !eveningEnd.isBlank()) ? eveningEnd : DEFAULT_EVENING_END;
        return start + " ~ " + end;
    }

    public String nightTimeOrDefault() {
        String start = (nightStart != null && !nightStart.isBlank()) ? nightStart : DEFAULT_NIGHT_START;
        String end = (nightEnd != null && !nightEnd.isBlank()) ? nightEnd : DEFAULT_NIGHT_END;
        return start + " ~ 익일 " + end;
    }
}

