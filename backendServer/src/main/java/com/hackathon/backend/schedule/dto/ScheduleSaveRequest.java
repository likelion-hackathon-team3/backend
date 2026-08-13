package com.hackathon.backend.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// POST /api/schedules 요청 본문.
// { "schedules": [ { "date": "...", "shift": "..." }, ... ] }
@Getter
@NoArgsConstructor
public class ScheduleSaveRequest {
    private List<ScheduleItemRequest> schedules;
}
