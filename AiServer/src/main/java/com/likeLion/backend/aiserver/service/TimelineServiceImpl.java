package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.ActivityType;
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

        List<TimelineItemDto> sanitizedItems = sanitizeAndSortTimelineItems(
                rawResponse.timelineItems(),
                normalizedRequest.currentTime(),
                normalizedRequest.currentWorkEnd()
        );

        return new TimelineGenerateResponse(
                targetDate,
                mode,
                rawResponse.pageTitle(),
                rawResponse.pageSubtitle(),
                sanitizedItems,
                rawResponse.recommendations()
        );
    }

    private List<TimelineItemDto> sanitizeAndSortTimelineItems(List<TimelineItemDto> items, String currentTime, String currentWorkEnd) {
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

        // 2. LocalDateTime 기반 자연 정렬
        normalizedList.sort((i1, i2) -> {
            try {
                java.time.LocalDateTime t1 = java.time.LocalDateTime.parse(i1.time().trim());
                java.time.LocalDateTime t2 = java.time.LocalDateTime.parse(i2.time().trim());
                return t1.compareTo(t2);
            } catch (Exception e) {
                return 0; // 예외 발생 시 순서 유지
            }
        });

        return normalizedList;
    }
}
