package com.hackathon.backend.timeline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

// GET /api/timeline 응답의 timelineItems[] 항목 하나.
// { "time": "23:30", "title": "...", "description": "...", "category": "MEAL", "highlight": null }
// highlight는 선택 필드라 null이면 응답에서 생략한다.
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineItemResponse {
    private String time;
    private String title;
    private String description;
    private String category;
    private String highlight;
}
