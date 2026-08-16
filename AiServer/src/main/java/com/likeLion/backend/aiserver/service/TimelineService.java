package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateRequest;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateResponse;

public interface TimelineService {
    TimelineGenerateResponse generateTimeline(TimelineGenerateRequest request);
}
