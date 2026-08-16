package com.likeLion.backend.aiserver.dto.timeline;

import java.util.List;

public record RawTimelineAiResponse(
        String aiSummary,
        List<TimelineBlockDto> timelineBlocks
) {
}
