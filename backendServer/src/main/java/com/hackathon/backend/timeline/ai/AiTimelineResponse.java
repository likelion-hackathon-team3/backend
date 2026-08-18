package com.hackathon.backend.timeline.ai;

import java.util.List;

// AiServer POST /api/timeline/generate 응답 본문(역직렬화 대상).
// targetDate/mode는 AiServer가 보낼 수 있지만 frontend 응답 계약에는 없는 필드라
// AiTimelineConverter가 절대 그대로 내보내지 않는다(내부 참고용으로만 받아둔다).
public record AiTimelineResponse(
        String targetDate,
        String mode,
        String pageTitle,
        String pageSubtitle,
        List<Item> timelineItems,
        List<String> recommendations
) {
    public record Item(
            String time,
            String title,
            String description,
            String category,
            String highlight
    ) {
    }
}
