package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TimelineSlotCalculator {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public TimelineSkeletonDto calculateSkeleton(TimelineGenerateRequest request) {
        LocalDateTime workEnd = parseDateTimeOrNull(request.currentWorkEnd());
        LocalDateTime workStart = parseDateTimeOrNull(request.nextWorkStart());
        int commuteMinutes = request.commuteMinutes() != null ? request.commuteMinutes() : 30;

        // 기준일 폴백
        LocalDate targetDate = request.targetDate() != null ? request.targetDate() : LocalDate.now();
        if (workStart == null) {
            workStart = targetDate.plusDays(1).atTime(7, 0); // 기본 익일 DAY
        }
        if (workEnd == null) {
            workEnd = targetDate.atTime(15, 0); // 기본 당일 15:00
        }

        // 0. 시작 기준점 계산 (currentTime이 workEnd보다 이후이면 currentTime부터 시작)
        LocalDateTime effectiveStart = workEnd;
        if (request.currentTime() != null && !request.currentTime().isBlank()) {
            try {
                LocalTime ct = LocalTime.parse(request.currentTime().trim(), TIME_FORMATTER);
                LocalDateTime parsedCurrent = targetDate.atTime(ct);
                if (parsedCurrent.isAfter(effectiveStart)) {
                    effectiveStart = parsedCurrent;
                }
            } catch (Exception ignored) {
            }
        }

        Duration totalDuration = Duration.between(effectiveStart, workStart);
        long totalFreeMinutes = Math.max(0, totalDuration.toMinutes());
        String totalFreeFormatted = formatDuration(totalDuration);

        List<BaseSlotDto> slots = new ArrayList<>();
        List<String> flexIntervals = new ArrayList<>();

        String transition = resolveTransitionType(request);

        // 1. 다음 근무가 OFF가 아닌 경우 출근 및 준비 슬롯 역산
        LocalDateTime prepTime = null;
        if (request.nextShift() != ShiftType.OFF) {
            int prepDuration = totalFreeMinutes < 600 ? 20 : 30; // 가용시간 촉박 시 준비 20분으로 압축
            prepTime = workStart.minusMinutes(commuteMinutes + prepDuration);

            slots.add(new BaseSlotDto(
                    formatIso(prepTime),
                    ActivityType.PREPARATION,
                    (long) prepDuration,
                    "출근 준비",
                    "통근 및 근무 준비"
            ));

            slots.add(new BaseSlotDto(
                    formatIso(workStart),
                    ActivityType.WORK,
                    480L, // 8시간
                    request.nextShift() != null ? request.nextShift().name() + " 근무 시작" : "근무 시작",
                    null
            ));
        }

        // 2. 교대 패턴별 핵심 수면/식사 슬롯 배치
        switch (transition) {
            case "EVENING_TO_DAY" -> {
                // 고위험 단축 교대: 수면 최우선 보호
                LocalDateTime sleepStart = workEnd.plusMinutes(45); // 귀가 및 샤워 45분
                LocalDateTime wakeTime = prepTime != null ? prepTime : workStart.minusMinutes(60);
                long sleepMinutes = Duration.between(sleepStart, wakeTime).toMinutes();

                if (sleepMinutes < 330) {
                    // 최소 5.5시간 강제 확보를 위해 취침 준비 극단적 단축
                    sleepStart = workEnd.plusMinutes(20);
                    sleepMinutes = Math.max(330, Duration.between(sleepStart, wakeTime).toMinutes());
                }

                slots.add(new BaseSlotDto(
                        formatIso(workEnd.plusMinutes(15)),
                        ActivityType.MEAL,
                        20L,
                        "귀가 후 가벼운 야식",
                        "소화가 잘되는 음식 섭취"
                ));

                slots.add(new BaseSlotDto(
                        formatIso(sleepStart),
                        ActivityType.SLEEP,
                        sleepMinutes,
                        "취침",
                        "권장 수면: " + (sleepMinutes / 60) + "시간 " + (sleepMinutes % 60) + "분"
                ));

                slots.add(new BaseSlotDto(
                        formatIso(wakeTime),
                        ActivityType.WAKE_UP,
                        10L,
                        "기상 및 환복",
                        null
                ));
            }
            case "DAY_TO_NIGHT", "OFF_TO_NIGHT" -> {
                // 야간 출근 전 사전 쪽잠(NAP) 확보
                LocalDateTime napEnd = prepTime != null ? prepTime.minusMinutes(15) : workStart.minusMinutes(90);
                LocalDateTime napStart = napEnd.minusMinutes(90); // 90분 낮잠

                slots.add(new BaseSlotDto(
                        formatIso(napStart.minusMinutes(60)),
                        ActivityType.MEAL,
                        40L,
                        "출근 전 식사",
                        "야간 근무를 위한 에너지 보충"
                ));

                slots.add(new BaseSlotDto(
                        formatIso(napStart),
                        ActivityType.NAP,
                        90L,
                        "사전 낮잠",
                        "권장 낮잠: 1시간 30분"
                ));

                slots.add(new BaseSlotDto(
                        formatIso(napEnd),
                        ActivityType.WAKE_UP,
                        15L,
                        "기상 및 각성",
                        "물 한 잔 및 스트레칭"
                ));

                // 가용시간이 충분하면 낮 시간 여유 구간 등록
                if (Duration.between(effectiveStart, napStart.minusMinutes(60)).toHours() >= 3) {
                    flexIntervals.add(formatIso(effectiveStart.plusHours(1)) + " ~ " + formatIso(napStart.minusMinutes(60)) + " (자유 여유 시간)");
                }
            }
            default -> {
                // 일반적인 전환 (DAY_TO_DAY, DAY_TO_EVENING 등)
                LocalDateTime sleepStart = targetDate.atTime(23, 30);
                if (sleepStart.isBefore(effectiveStart)) {
                    sleepStart = effectiveStart.plusMinutes(45);
                }

                // 정상 수면 시간 상한(7.5~8시간) 적용
                long maxSleepMinutes = 480L; // 최대 8시간
                long naturalSleepMinutes = 450L; // 표준 7.5시간

                LocalDateTime maxWakeTime = prepTime != null ? prepTime.minusMinutes(20) : workStart.minusMinutes(60);
                long availableSleep = Duration.between(sleepStart, maxWakeTime).toMinutes();

                long sleepMinutes = Math.min(naturalSleepMinutes, availableSleep);
                LocalDateTime wakeTime = sleepStart.plusMinutes(sleepMinutes);

                // 퇴근 직후/현재 직후 식사 (취침 최소 1시간 전)
                LocalDateTime dinnerTime = effectiveStart.plusMinutes(30);
                if (dinnerTime.plusMinutes(40).isBefore(sleepStart)) {
                    slots.add(new BaseSlotDto(
                            formatIso(dinnerTime),
                            ActivityType.MEAL,
                            40L,
                            "저녁 식사 및 휴식",
                            null
                    ));
                }

                slots.add(new BaseSlotDto(
                        formatIso(sleepStart),
                        ActivityType.SLEEP,
                        sleepMinutes,
                        "취침",
                        "권장 수면: " + (sleepMinutes / 60) + "시간 " + (sleepMinutes % 60) + "분"
                ));

                slots.add(new BaseSlotDto(
                        formatIso(wakeTime),
                        ActivityType.WAKE_UP,
                        15L,
                        "기상",
                        null
                ));

                // 기상 후 출근 준비(prepTime)까지 3시간 이상 여유가 있는 경우 (예: EVENING 출근)
                if (prepTime != null && Duration.between(wakeTime, prepTime).toHours() >= 3) {
                    LocalDateTime lunchTime = prepTime.minusHours(1).minusMinutes(30);
                    slots.add(new BaseSlotDto(
                            formatIso(lunchTime),
                            ActivityType.MEAL,
                            45L,
                            "점심 식사",
                            "출근 전 든든한 식사"
                    ));

                    flexIntervals.add(formatIso(wakeTime.plusMinutes(30)) + " ~ " + formatIso(lunchTime.minusMinutes(15)) + " (오전/낮 여유 시간)");
                }
            }
        }

        // 3. 과거 시간(effectiveStart 이전) 슬롯 자동 제외
        final LocalDateTime startBoundary = effectiveStart;
        slots.removeIf(slot -> {
            LocalDateTime slotTime = parseDateTimeOrNull(slot.time());
            return slotTime != null && slotTime.isBefore(startBoundary);
        });

        // 3. 시간순 정렬
        slots.sort(Comparator.comparing(BaseSlotDto::time));

        return new TimelineSkeletonDto(slots, flexIntervals, totalFreeFormatted);
    }

    private String resolveTransitionType(TimelineGenerateRequest request) {
        if (request.transitionType() != null && !request.transitionType().isBlank()) {
            return request.transitionType().trim();
        }
        ShiftType cur = request.currentShift() != null ? request.currentShift() : ShiftType.OFF;
        ShiftType nxt = request.nextShift() != null ? request.nextShift() : ShiftType.OFF;
        return cur.name() + "_TO_" + nxt.name();
    }

    private LocalDateTime parseDateTimeOrNull(String isoString) {
        if (isoString == null || isoString.isBlank() || isoString.equals("해당 없음")) {
            return null;
        }
        try {
            return LocalDateTime.parse(isoString.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String formatIso(LocalDateTime dt) {
        return dt.format(ISO_FORMATTER).substring(0, 16);
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (minutes > 0) {
            return hours + "시간 " + minutes + "분";
        }
        return hours + "시간";
    }
}
