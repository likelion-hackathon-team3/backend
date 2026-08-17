package com.hackathon.backend.timeline.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// GET /api/timeline 성공 응답의 data 부분.
@Getter
@AllArgsConstructor
public class TimelineData {
    private String pageTitle;
    private String pageSubtitle;
    private List<TimelineItemResponse> timelineItems;
    private List<String> recommendations;
}
