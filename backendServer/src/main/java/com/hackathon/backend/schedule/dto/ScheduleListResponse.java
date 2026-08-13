package com.hackathon.backend.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// GET /api/schedules?month=... 응답.
// { "schedules": [ ... ] }  (명세상 success 필드는 넣지 않는다)
@Getter
@AllArgsConstructor
public class ScheduleListResponse {
    private List<ScheduleItemResponse> schedules;
}
