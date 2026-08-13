package com.hackathon.backend.environment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 요청 JSON 안의 한 근무 시간대: { "start": "HH:mm", "end": "HH:mm" }
// 명세대로 start/end는 문자열로 받는다(검증/변환은 Service에서 처리).
@Getter
@NoArgsConstructor
public class ShiftTimeRequest {
    private String start; // "HH:mm"
    private String end;   // "HH:mm"
}
