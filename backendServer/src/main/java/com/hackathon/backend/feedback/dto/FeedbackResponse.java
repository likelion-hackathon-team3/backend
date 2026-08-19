package com.hackathon.backend.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// POST /api/feedback 응답.
// 성공: { "success": true, "message": "피드백이 성공적으로 등록되었습니다." }
// 실패: { "success": false, "message": "..." }
// 형식/범위 오류 메시지는 정상 프론트 흐름에서는 나오지 않는 방어적 오류 응답이다.
@Getter
@AllArgsConstructor
public class FeedbackResponse {
    private boolean success;
    private String message;

    public static FeedbackResponse ok() {
        return new FeedbackResponse(true, "피드백이 성공적으로 등록되었습니다.");
    }

    // DELETE /api/feedback?feedbackDate=... 성공
    public static FeedbackResponse deleted() {
        return new FeedbackResponse(true, "삭제되었습니다.");
    }

    // DELETE /api/feedback?feedbackDate=... : 해당 날짜의 Feedback이 없음
    public static FeedbackResponse notFound() {
        return new FeedbackResponse(false, "해당 날짜에 등록된 피드백이 없습니다.");
    }

    // 필수 피드백 항목 누락 (feedbackDate/actualSleepDuration/caffeineIntake.taken/
    // postShiftFatigue/routineHelpfulness 중 하나라도 없음)
    public static FeedbackResponse missing() {
        return new FeedbackResponse(false, "필수 피드백 항목이 누락되었습니다.");
    }

    // feedbackDate 또는 caffeineIntake.lastTime 형식 오류
    public static FeedbackResponse invalidFormat() {
        return new FeedbackResponse(false, "날짜 또는 시간 형식이 올바르지 않습니다.");
    }

    public static FeedbackResponse fatigueOutOfRange() {
        return new FeedbackResponse(false, "피로도 점수는 1에서 10 사이여야 합니다.");
    }

    public static FeedbackResponse helpfulnessOutOfRange() {
        return new FeedbackResponse(false, "루틴 도움 정도 점수는 1에서 5 사이여야 합니다.");
    }
}
