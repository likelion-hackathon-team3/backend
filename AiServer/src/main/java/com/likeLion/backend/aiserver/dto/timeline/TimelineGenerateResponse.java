package com.likeLion.backend.aiserver.dto.timeline;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "AI 추천 타임라인 생성 응답 DTO")
public record TimelineGenerateResponse(
        @Schema(description = "대상 날짜 (YYYY-MM-DD)", example = "2026-07-12")
        LocalDate targetDate,

        @Schema(description = "타임라인 생성 모드 (TODAY | FUTURE)", example = "TODAY")
        TimelineMode mode,

        @Schema(description = "메인 타이틀 (상단)", example = "오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요")
        String pageTitle,

        @Schema(description = "서브 타이틀 (상단)", example = "회복을 최우선으로 한 개인 맞춤 루틴입니다.")
        String pageSubtitle,

        @Schema(description = "시간순으로 정렬된 AI 웰니스 타임라인 리스트")
        List<TimelineItemDto> timelineItems,

        @Schema(description = "AI 맞춤 추천 포인트 리스트 (우측)", example = "[\"오늘은 수면 확보가 가장 중요해요.\", \"카페인은 14시 이후 섭취를 피해 주세요.\"]")
        List<String> recommendations
) {
}
