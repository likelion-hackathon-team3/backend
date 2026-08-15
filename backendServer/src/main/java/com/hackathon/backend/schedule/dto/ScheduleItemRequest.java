package com.hackathon.backend.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 요청 JSON 안의 근무표 한 칸.
// 명세대로 date/shift는 문자열로 받는다(검증/에러 제어를 위해 String으로 받음).
@Getter
@NoArgsConstructor
public class ScheduleItemRequest {
    private String date;   // "YYYY-MM-DD"
    private String shift;  // "DAY" | "EVENING" | "NIGHT" | "OFF"
}
