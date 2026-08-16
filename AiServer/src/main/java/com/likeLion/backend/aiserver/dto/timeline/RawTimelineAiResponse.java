package com.likeLion.backend.aiserver.dto.timeline;

import java.util.List;

public record RawTimelineAiResponse(
        String pageTitle,
        String pageSubtitle,
        List<TimelineItemDto> timelineItems,
        List<String> recommendations
) {
}
