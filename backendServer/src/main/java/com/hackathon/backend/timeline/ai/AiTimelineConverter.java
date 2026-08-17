package com.hackathon.backend.timeline.ai;

import com.hackathon.backend.timeline.dto.TimelineData;
import com.hackathon.backend.timeline.dto.TimelineItemResponse;

import java.util.List;

// AiServer 응답(AiTimelineResponse)을 기존 GET /api/timeline 응답 계약(TimelineData)으로 변환한다.
// targetDate/mode는 여기서 의도적으로 버린다(frontend 응답 계약에 없는 필드).
public class AiTimelineConverter {

    public TimelineData convert(AiTimelineResponse response) {
        List<TimelineItemResponse> items = response.timelineItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new TimelineData(
                response.pageTitle(),
                response.pageSubtitle(),
                items,
                response.recommendations()
        );
    }

    private TimelineItemResponse toItemResponse(AiTimelineResponse.Item item) {
        return new TimelineItemResponse(
                item.time(),
                item.title(),
                item.description(),
                item.category(),
                item.highlight()
        );
    }
}
