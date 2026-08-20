package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.ActivityType;
import com.likeLion.backend.aiserver.dto.timeline.RawTimelineAiResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateRequest;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineItemDto;
import com.likeLion.backend.aiserver.dto.timeline.TimelineMode;
import com.likeLion.backend.aiserver.dto.timeline.TimelineSkeletonDto;
import com.likeLion.backend.aiserver.service.layer.TimelineAiGenerator;
import com.likeLion.backend.aiserver.service.layer.TimelineSlotCalculator;
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

    private final TimelineAiGenerator timelineAiGenerator;
    private final TimelineSlotCalculator timelineSlotCalculator;

    public TimelineServiceImpl(TimelineAiGenerator timelineAiGenerator, TimelineSlotCalculator timelineSlotCalculator) {
        this.timelineAiGenerator = timelineAiGenerator;
        this.timelineSlotCalculator = timelineSlotCalculator != null ? timelineSlotCalculator : new TimelineSlotCalculator();
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

        TimelineSkeletonDto skeleton = timelineSlotCalculator.calculateSkeleton(normalizedRequest);

        RawTimelineAiResponse rawResponse;
        if (mode == TimelineMode.TODAY) {
            rawResponse = timelineAiGenerator.generateTodayTimeline(normalizedRequest, skeleton);
        } else {
            rawResponse = timelineAiGenerator.generateFutureTimeline(normalizedRequest, skeleton);
        }

        List<TimelineItemDto> sanitizedItems = sanitizeAndSortTimelineItems(rawResponse.timelineItems());

        return new TimelineGenerateResponse(
                targetDate,
                mode,
                rawResponse.pageTitle(),
                rawResponse.pageSubtitle(),
                sanitizedItems,
                rawResponse.recommendations()
        );
    }

    private List<TimelineItemDto> sanitizeAndSortTimelineItems(List<TimelineItemDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        // 1. 카테고리 안전 보정 (null이거나 유효하지 않은 경우 REST로 기본 대체)
        List<TimelineItemDto> normalizedList = new ArrayList<>(items.size());
        for (TimelineItemDto item : items) {
            if (item == null) continue;
            ActivityType category = item.category() != null ? item.category() : ActivityType.REST;
            normalizedList.add(new TimelineItemDto(
                    item.time(),
                    item.title(),
                    item.description(),
                    category,
                    item.highlight()
            ));
        }

        if (normalizedList.size() <= 1) {
            return normalizedList;
        }

        // 2. LocalDateTime 기반 자연 정렬 (파싱 실패 항목은 뒤로 배치)
        normalizedList.sort(Comparator.comparing(
                item -> parseLocalDateTimeOrNull(item.time()),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        return normalizedList;
    }

    private java.time.LocalDateTime parseLocalDateTimeOrNull(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDateTime.parse(timeStr.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
