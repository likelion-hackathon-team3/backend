package com.likeLion.backend.aiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "근무표 인식 응답 DTO")
public record ScheduleOcrResponse(
        @Schema(description = "인식 성공 여부", example = "true")
        boolean success,

        @Schema(description = "인식된 날짜별 근무 목록")
        List<RecognizedScheduleDto> recognizedSchedules,

        @Schema(description = "인식 실패한 날짜 목록 (YYYY-MM-DD)", example = "[\"2026-08-12\"]")
        List<String> failedDates,

        @Schema(description = "실패 메시지 (실패 시에만 포함)", example = "이미지를 인식할 수 없습니다.")
        String message
) {
    public static ScheduleOcrResponse success(List<RecognizedScheduleDto> recognizedSchedules, List<String> failedDates) {
        return new ScheduleOcrResponse(true, recognizedSchedules, failedDates, null);
    }

    public static ScheduleOcrResponse failure(String message) {
        return new ScheduleOcrResponse(false, null, null, message);
    }
}
