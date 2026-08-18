package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.RawTimelineAiResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateRequest;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineItemDto;
import com.likeLion.backend.aiserver.dto.timeline.TimelineMode;
import com.likeLion.backend.aiserver.service.layer.TimelineAiGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TimelineServiceImpl implements TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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
                request.personalization(),
                request.analysisResult()
        );

        log.info("Timeline generation requested for targetDate: {}, transition: {}, currentTime: {}, workEnd: {}, nextStart: {}, commuteMin: {}, hasPersonalization: {}, hasAnalysis: {}",
                targetDate, transitionType, request.currentTime(), request.currentWorkEnd(), request.nextWorkStart(), request.commuteMinutes(), normalizedRequest.personalization() != null, normalizedRequest.analysisResult() != null);

        TimelineMode mode = (normalizedRequest.analysisResult() != null) ? TimelineMode.TODAY : TimelineMode.FUTURE;

        RawTimelineAiResponse rawResponse;
        if (mode == TimelineMode.TODAY) {
            rawResponse = timelineAiGenerator.generateTodayTimeline(normalizedRequest);
        } else {
            rawResponse = timelineAiGenerator.generateFutureTimeline(normalizedRequest);
        }

        List<TimelineItemDto> sortedItems = sortTimelineItems(rawResponse.timelineItems(), normalizedRequest.currentTime());

        return new TimelineGenerateResponse(
                targetDate,
                mode,
                rawResponse.pageTitle(),
                rawResponse.pageSubtitle(),
                sortedItems,
                rawResponse.recommendations()
        );
    }

    private List<TimelineItemDto> sortTimelineItems(List<TimelineItemDto> items, String currentTime) {
        if (items == null || items.size() <= 1) {
            return items;
        }

        int anchorMinutes = parseAnchorMinutes(currentTime, items);

        List<TimelineItemDto> sortedList = new ArrayList<>(items);
        sortedList.sort(Comparator.comparingInt(item -> toOffsetMinutes(item.time(), anchorMinutes)));
        return sortedList;
    }

    private int parseAnchorMinutes(String currentTime, List<TimelineItemDto> items) {
        if (currentTime != null && !currentTime.isBlank()) {
            try {
                LocalTime time = LocalTime.parse(currentTime.trim(), TIME_FORMATTER);
                return time.getHour() * 60 + time.getMinute();
            } catch (Exception ignored) {
            }
        }
        for (TimelineItemDto item : items) {
            if (item != null && item.time() != null && !item.time().isBlank()) {
                try {
                    LocalTime time = LocalTime.parse(item.time().trim(), TIME_FORMATTER);
                    return time.getHour() * 60 + time.getMinute();
                } catch (Exception ignored) {
                }
            }
        }
        return 0;
    }

    private int toOffsetMinutes(String timeStr, int anchorMinutes) {
        if (timeStr == null || timeStr.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            LocalTime time = LocalTime.parse(timeStr.trim(), TIME_FORMATTER);
            int minutes = time.getHour() * 60 + time.getMinute();
            if (minutes < anchorMinutes) {
                return minutes + 1440;
            }
            return minutes;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
