package com.hackathon.backend.schedule.dto;

import com.hackathon.backend.schedule.Schedule;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 응답 JSON 안의 근무표 한 칸.
// 내부의 LocalDate/ShiftType을 명세대로 다시 문자열로 바꿔서 내보낸다.
@Getter
@AllArgsConstructor
public class ScheduleItemResponse {
    private String date;
    private String shift;

    public static ScheduleItemResponse from(Schedule schedule) {
        return new ScheduleItemResponse(
                schedule.getDate().toString(),   // LocalDate -> "YYYY-MM-DD"
                schedule.getShift().name()       // ShiftType -> "DAY" 등
        );
    }
}
