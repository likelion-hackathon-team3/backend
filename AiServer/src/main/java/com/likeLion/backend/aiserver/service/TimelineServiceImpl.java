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

        // 2. 앵커 기준 시간(시작 시각) 파악
        int anchorMinutes = parseAnchorMinutes(currentTime, currentWorkEnd, normalizedList);

        // 3. 시간 오프셋 기반 정렬 (WORK 항목은 다음 근무 시작이므로 앵커와 시간이 같거나 앞서면 다음날(+1440)로 간주)
        normalizedList.sort(Comparator.comparingInt(item -> toOffsetMinutes(item, anchorMinutes)));

        return normalizedList;
    }

    private int parseAnchorMinutes(String currentTime, String currentWorkEnd, List<TimelineItemDto> items) {
        // 1. currentWorkEnd가 가장 확실한 트랜지션의 시작점이므로 최우선 순위로 파싱
        if (currentWorkEnd != null && !currentWorkEnd.isBlank() && !currentWorkEnd.equals("해당 없음")) {
            try {
                // "2026-08-20T15:00" 또는 "15:00" 형태 파싱
                String timePart = currentWorkEnd.contains("T") ? currentWorkEnd.substring(currentWorkEnd.indexOf("T") + 1) : currentWorkEnd;
                if (timePart.length() >= 5) {
                    LocalTime time = LocalTime.parse(timePart.substring(0, 5), TIME_FORMATTER);
                    return time.getHour() * 60 + time.getMinute();
                }
            } catch (Exception ignored) {
            }
        }

        // 2. currentWorkEnd가 없다면(예: OFF 상태) 현재 시간을 기준점으로 삼음
        if (currentTime != null && !currentTime.isBlank()) {
            try {
                LocalTime time = LocalTime.parse(currentTime.trim(), TIME_FORMATTER);
                return time.getHour() * 60 + time.getMinute();
            } catch (Exception ignored) {
            }
        }

        // 3. 둘 다 불가능하다면 AI가 응답한 첫 번째 항목의 시간을 기준점으로 폴백
        for (TimelineItemDto item : items) {
            if (item != null && item.time() != null && !item.time().isBlank()) {
                try {
                    LocalTime time = LocalTime.parse(item.time().trim(), TIME_FORMATTER);
                    return time.getHour() * 60 + time.getMinute();
                } catch (Exception ignored) {
                }
            }
        }
        
        return 0; // 최후의 수단: 자정
    }

    private int toOffsetMinutes(TimelineItemDto item, int anchorMinutes) {
        String timeStr = item.time();
        if (timeStr == null || timeStr.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            LocalTime time = LocalTime.parse(timeStr.trim(), TIME_FORMATTER);
            int minutes = time.getHour() * 60 + time.getMinute();

            // WORK 항목은 다음 근무 시작을 나타내므로 앵커 시각 이하이면 24시간 뒤(+1440분)로 오프셋 부여
            if (item.category() == ActivityType.WORK) {
                return (minutes <= anchorMinutes) ? (minutes + 1440) : minutes;
            }

            if (minutes < anchorMinutes) {
                return minutes + 1440;
            }
            return minutes;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
