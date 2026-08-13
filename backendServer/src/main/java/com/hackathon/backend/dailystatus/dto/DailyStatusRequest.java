package com.hackathon.backend.dailystatus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// POST /api/daily-status 요청 본문.
// { "fatigueLevel": "높음" }
// 명세대로 한글 문자열로 받는다(검증/변환은 Service에서 처리).
@Getter
@NoArgsConstructor
public class DailyStatusRequest {
    private String fatigueLevel; // "낮음" | "보통" | "높음"
}
