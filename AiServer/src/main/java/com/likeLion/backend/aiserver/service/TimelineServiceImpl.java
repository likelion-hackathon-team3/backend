package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.RawTimelineAiResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateRequest;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineMode;
import com.likeLion.backend.aiserver.service.layer.TimelineAiGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TimelineServiceImpl implements TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineServiceImpl.class);

    private final TimelineAiGenerator timelineAiGenerator;

    public TimelineServiceImpl(TimelineAiGenerator timelineAiGenerator) {
        this.timelineAiGenerator = timelineAiGenerator;
    }

    @Override
    public TimelineGenerateResponse generateTimeline(TimelineGenerateRequest request) {
        LocalDate targetDate = request.targetDate() != null ? request.targetDate() : LocalDate.now();
        ShiftType currentShift = request.currentShift() != null ? request.currentShift() : ShiftType.OFF;
        ShiftType nextShift = request.nextShift() != null ? request.nextShift() : ShiftType.OFF;

        String transitionType = request.transitionType();
        if (transitionType == null || transitionType.isBlank()) {
            transitionType = currentShift.name() + "_TO_" + nextShift.name();
        }

        TimelineGenerateRequest normalizedRequest = new TimelineGenerateRequest(
                targetDate,
                currentShift,
                nextShift,
                transitionType,
                request.currentTime(),
                request.currentWorkEnd(),
                request.nextWorkStart(),
                request.commuteMinutes(),
                request.userNotes(),
                request.shiftTimes(),
                request.analysisResult()
        );

        log.info("Timeline generation requested for targetDate: {}, transition: {}, currentTime: {}, workEnd: {}, nextStart: {}, commuteMin: {}, hasAnalysis: {}",
                targetDate, transitionType, request.currentTime(), request.currentWorkEnd(), request.nextWorkStart(), request.commuteMinutes(), normalizedRequest.analysisResult() != null);

        TimelineMode mode = (normalizedRequest.analysisResult() != null) ? TimelineMode.TODAY : TimelineMode.FUTURE;

        RawTimelineAiResponse rawResponse;
        if (mode == TimelineMode.TODAY) {
            rawResponse = timelineAiGenerator.generateTodayTimeline(normalizedRequest);
        } else {
            rawResponse = timelineAiGenerator.generateFutureTimeline(normalizedRequest);
        }

        return new TimelineGenerateResponse(
                targetDate,
                mode,
                rawResponse.pageTitle(),
                rawResponse.pageSubtitle(),
                rawResponse.timelineItems(),
                rawResponse.recommendations()
        );
    }
}
