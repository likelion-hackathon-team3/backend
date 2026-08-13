package com.likeLion.backend.aiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RecognizedScheduleDto(
        @Schema(description = "날짜 (YYYY-MM-DD)", example = "2026-08-10")
        String date,

        @Schema(description = "근무 유형 (DAY / EVENING / NIGHT / OFF)", example = "DAY")
        String shift,

        @Schema(description = "인식 정확도 (0~100)", example = "100")
        int confidence
) {}
