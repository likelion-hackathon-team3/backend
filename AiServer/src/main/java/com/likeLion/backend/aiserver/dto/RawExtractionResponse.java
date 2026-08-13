package com.likeLion.backend.aiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Raw 텍스트 추출 DTO (Vision Layer 결과물)")
public record RawExtractionResponse(
        @Schema(description = "추출 성공 여부")
        boolean success,

        @Schema(description = "날짜별 추출된 원문 텍스트 기호 목록")
        List<RawScheduleItem> items,

        @Schema(description = "인식 실패한 날짜 목록 (YYYY-MM-DD)")
        List<String> failedDates,

        @Schema(description = "에러 메시지")
        String message
) {
    public record RawScheduleItem(
            String date,      // YYYY-MM-DD
            String rawShift,  // 원문 글자 (예: D, E, N, 야, /, 오프, 연차 등, 글자가 없는 경우 빈값/null)
            String cellColor  // 셀의 배경 색상 (예: YELLOW, RED, BLUE, GREEN, PINK, WHITE 등 또는 "NONE")
    ) {}

    public static RawExtractionResponse success(List<RawScheduleItem> items, List<String> failedDates) {
        return new RawExtractionResponse(true, items, failedDates, null);
    }

    public static RawExtractionResponse failure(String message) {
        return new RawExtractionResponse(false, null, null, message);
    }
}
