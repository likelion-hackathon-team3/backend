package com.hackathon.backend.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// DELETE /api/schedules?date=... 응답.
// 성공: { "success": true,  "message": "삭제되었습니다." }
// 없음: { "success": false, "message": "해당 날짜에 등록된 근무표가 없습니다." }
@Getter
@AllArgsConstructor
public class ScheduleDeleteResponse {
    private boolean success;
    private String message;
}
