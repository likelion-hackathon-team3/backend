package com.hackathon.backend.feedback.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// POST /api/feedback 요청의 caffeineIntake 중첩 객체.
// { "taken": true, "lastTime": "15:30" }
// taken=true면 lastTime 필수(null/blank면 필수값 누락 실패), 값이 있으면 "HH:mm" 형식 검증.
// taken=false면 lastTime은 선택이며, 값이 오더라도 FeedbackService가 null로 정규화해서 저장한다.
@Getter
@NoArgsConstructor
public class CaffeineIntakeRequest {
    private Boolean taken;
    private String lastTime; // "HH:mm"
}
