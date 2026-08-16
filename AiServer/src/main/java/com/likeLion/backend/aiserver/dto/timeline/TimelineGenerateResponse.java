package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "AI 추천 타임라인 생성 응답 DTO")
public record TimelineGenerateResponse(
        @Schema(description = "대상 날짜 (YYYY-MM-DD)", example = "2026-08-17")
        LocalDate targetDate,

        @Schema(description = "타임라인 생성 모드 (TODAY | FUTURE)", example = "TODAY")
        TimelineMode mode,

        @Schema(description = "AI 맞춤 종합 조언 및 응원 메시지", example = "오늘 DAY 근무 후 밤 NIGHT 출근이 예정되어 있습니다. 오후에 암막 커튼을 치고 2시간 정도 낮잠을 꼭 취해주세요!")
        String aiSummary,

        @Schema(description = "추천 타임라인 블록 리스트")
        List<TimelineBlockDto> timelineBlocks
) {
}
