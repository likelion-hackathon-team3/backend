package com.hackathon.backend.feedback.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// POST /api/feedback 요청 본문.
// transitionType은 요청에 포함되지 않는다 — Spring이 Schedule로 내부 계산한다.
@Getter
@NoArgsConstructor
public class FeedbackRequest {
    private String feedbackDate; // 피드백 대상 Timeline의 기준 날짜, "YYYY-MM-DD"
    private Double actualSleepDuration; // 실제 수면 시간(시간 단위)
    private CaffeineIntakeRequest caffeineIntake;
    private Integer postShiftFatigue; // 1~10
    private Integer routineHelpfulness; // 1~5
}
