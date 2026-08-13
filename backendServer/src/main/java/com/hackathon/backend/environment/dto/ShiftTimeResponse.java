package com.hackathon.backend.environment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 응답 JSON 안의 한 근무 시간대: { "start": "HH:mm", "end": "HH:mm" }
// 내부의 LocalTime을 명세대로 다시 "HH:mm" 문자열로 바꿔서 내보낸다.
@Getter
@AllArgsConstructor
public class ShiftTimeResponse {
    private String start;
    private String end;
}
