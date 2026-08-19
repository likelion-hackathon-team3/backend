package com.hackathon.backend.feedback;

import com.hackathon.backend.feedback.dto.FeedbackRequest;
import com.hackathon.backend.feedback.dto.FeedbackResponse;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: POST/DELETE /api/feedback
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    // 피드백 저장 (body 누락 시에도 500 대신 명세의 실패 응답으로 처리)
    @PostMapping
    public FeedbackResponse save(@RequestBody(required = false) FeedbackRequest request) {
        return feedbackService.save(request);
    }

    // 특정 날짜 피드백 삭제 (예: /api/feedback?feedbackDate=2026-08-10)
    @DeleteMapping
    public FeedbackResponse delete(@RequestParam String feedbackDate) {
        return feedbackService.delete(feedbackDate);
    }
}
