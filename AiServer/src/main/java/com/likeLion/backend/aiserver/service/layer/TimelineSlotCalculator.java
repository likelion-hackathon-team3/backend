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

    // ==========================================
    // 🕒 표준 소요 시간 범위 상수 (Duration Range Constants)
    // ==========================================
    // 1. 수면 (SLEEP): 최소 0시간(0분) ~ 기본 7.5시간(450분) ~ 최대 8.5시간(510분)
    public static final long MIN_SLEEP_MINUTES = 0L;
    public static final long DEFAULT_SLEEP_MINUTES = 450L;
    public static final long MAX_SLEEP_MINUTES = 510L;

    // 2. 쪽잠 (NAP): 최소 30분 ~ 기본 90분 ~ 최대 90분
    public static final long MIN_NAP_MINUTES = 30L;
    public static final long DEFAULT_NAP_MINUTES = 90L;

    // 3. 식사 (MEAL): 최소 20분(단축 야식) ~ 기본 40분 ~ 최대 45분
    public static final long MIN_MEAL_MINUTES = 20L;
    public static final long DEFAULT_MEAL_MINUTES = 40L;
    public static final long MAX_MEAL_MINUTES = 45L;

    // 4. 출근 준비 (PREPARATION): 최소 20분(단축) ~ 기본 30분
    public static final long MIN_PREP_MINUTES = 20L;
    public static final long DEFAULT_PREP_MINUTES = 30L;

    // ==========================================
    // ⏰ 표준 생체 리듬 시간대 (Standard Time Windows)
    // ==========================================
    public static final LocalTime DEFAULT_BEDTIME = LocalTime.of(23, 30);
    public static final LocalTime DEFAULT_LUNCH_TIME = LocalTime.of(12, 30);

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

        // 0. 시작 기준점 계산: 퇴근 후 귀가 완료 시각 = workEnd + commuteMinutes
        LocalDateTime homeArrival = (request.currentShift() != ShiftType.OFF) ? workEnd.plusMinutes(commuteMinutes) : workEnd;
        LocalDateTime effectiveStart = homeArrival;
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
            long prepDuration = (totalFreeMinutes < 300) ? MIN_PREP_MINUTES : DEFAULT_PREP_MINUTES;
            prepTime = workStart.minusMinutes(commuteMinutes + prepDuration);

            slots.add(new BaseSlotDto(
                    formatIso(prepTime),
                    ActivityType.PREPARATION,
                    prepDuration,
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

        // 2. 교대 패턴별 핵심 수면/식사 슬롯 배치 (상수 범위 적용)
        int sleepBuffer = (request.personalization() != null) ? request.personalization().sleepBufferOrDefault() : 0;

        switch (transition) {
            case "EVENING_TO_DAY" -> {
                // 고위험 단축 교대
                LocalDateTime wakeTime = prepTime != null ? prepTime.minusMinutes(10) : workStart.minusMinutes(60);
                LocalDateTime mealTime = effectiveStart.plusMinutes(15);
                LocalDateTime sleepStart = effectiveStart.plusMinutes(35);

                long availableSleep = Duration.between(sleepStart, wakeTime).toMinutes();

                if (availableSleep >= 240) {
                    // 4시간 이상: SLEEP
                    long sleepMinutes = Math.min(DEFAULT_SLEEP_MINUTES, availableSleep);
                    slots.add(new BaseSlotDto(formatIso(mealTime), ActivityType.MEAL, MIN_MEAL_MINUTES, "귀가 후 가벼운 야식", "소화가 잘되는 음식 섭취"));
                    slots.add(new BaseSlotDto(formatIso(sleepStart), ActivityType.SLEEP, sleepMinutes, "취침", "권장 수면: " + (sleepMinutes / 60) + "시간 " + (sleepMinutes % 60) + "분"));
                    slots.add(new BaseSlotDto(formatIso(wakeTime), ActivityType.WAKE_UP, 10L, "기상 및 환복", null));
                } else if (availableSleep >= 40) {
                    // 40분 ~ 4시간 미만 (초단축 2~3시간 휴식 후 출근): 쪽잠(NAP)
                    long napMinutes = Math.min(DEFAULT_NAP_MINUTES, availableSleep - 10);
                    slots.add(new BaseSlotDto(formatIso(sleepStart), ActivityType.NAP, napMinutes, "단기 쪽잠 및 휴식", "빠른 피로 회복을 위한 짧은 수면"));
                    slots.add(new BaseSlotDto(formatIso(sleepStart.plusMinutes(napMinutes)), ActivityType.WAKE_UP, 10L, "기상 및 출근 준비", null));
                } else {
                    // 40분 미만: 수면 0시간, 휴식만 제공
                    slots.add(new BaseSlotDto(formatIso(effectiveStart), ActivityType.REST, Math.max(10, totalFreeMinutes - 40), "가벼운 휴식 및 이동", "출근 전 에너지 음료 및 스트레칭"));
                }
            }
            case "DAY_TO_NIGHT", "OFF_TO_NIGHT" -> {
                // 야간 출근 전 사전 쪽잠(NAP, 60~90분) 확보
                LocalDateTime napEnd = prepTime != null ? prepTime.minusMinutes(15) : workStart.minusMinutes(90);
                LocalDateTime napStart = napEnd.minusMinutes(DEFAULT_NAP_MINUTES);

                slots.add(new BaseSlotDto(
                        formatIso(napStart.minusMinutes(DEFAULT_MEAL_MINUTES + 10)),
                        ActivityType.MEAL,
                        DEFAULT_MEAL_MINUTES,
                        "출근 전 식사",
                        "야간 근무를 위한 에너지 보충"
                ));

                slots.add(new BaseSlotDto(
                        formatIso(napStart),
                        ActivityType.NAP,
                        DEFAULT_NAP_MINUTES,
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
            case "NIGHT_TO_DAY" -> {
                // 생체 리듬 전환 (1차 주간 수면 + 2차 야간 조기 수면)
                LocalDateTime sleep1Start = workEnd.plusMinutes(90);
                LocalDateTime sleep1End = sleep1Start.plusHours(5);

                slots.add(new BaseSlotDto(
                        formatIso(sleep1Start),
                        ActivityType.SLEEP,
                        300L,
                        "퇴근 후 1차 회복 수면",
                        "암막 커튼 및 안대 필수"
                ));
                slots.add(new BaseSlotDto(
                        formatIso(sleep1End),
                        ActivityType.WAKE_UP,
                        15L,
                        "기상 및 환기",
                        "햇볕 쬐기"
                ));

                // 오후 여유 및 식사
                slots.add(new BaseSlotDto(
                        formatIso(sleep1End.plusHours(1)),
                        ActivityType.MEAL,
                        DEFAULT_MEAL_MINUTES,
                        "점심 식사",
                        null
                ));

                // 2차 수면 (22:30 ~ 05:30)
                LocalDateTime sleep2Start = targetDate.atTime(22, 30);
                LocalDateTime wake2Time = prepTime != null ? prepTime : workStart.minusMinutes(60);
                long sleep2Minutes = Duration.between(sleep2Start, wake2Time).toMinutes();

                slots.add(new BaseSlotDto(
                        formatIso(sleep2Start),
                        ActivityType.SLEEP,
                        sleep2Minutes,
                        "익일 출근 대비 2차 수면",
                        "조기 취침"
                ));
                slots.add(new BaseSlotDto(
                        formatIso(wake2Time),
                        ActivityType.WAKE_UP,
                        15L,
                        "기상",
                        null
                ));
            }
            default -> {
                // 일반적인 전환 (DAY_TO_DAY, DAY_TO_EVENING, OFF_TO_DAY 등)
                LocalDateTime sleepStart = targetDate.atTime(DEFAULT_BEDTIME);
                if (sleepStart.isBefore(effectiveStart)) {
                    sleepStart = effectiveStart.plusMinutes(45);
                }

                // 수면 시간 범위(MIN 0분 ~ MAX 510분) 상수 적용
                long desiredSleep = Math.min(MAX_SLEEP_MINUTES, DEFAULT_SLEEP_MINUTES + sleepBuffer);

                LocalDateTime maxWakeTime = prepTime != null ? prepTime.minusMinutes(20) : workStart.minusMinutes(60);
                long availableSleep = Duration.between(sleepStart, maxWakeTime).toMinutes();

                if (availableSleep >= 240) {
                    long sleepMinutes = Math.min(desiredSleep, availableSleep);
                    LocalDateTime wakeTime = sleepStart.plusMinutes(sleepMinutes);

                    // 저녁 식사 및 휴식 (귀가 후 10분 뒤)
                    LocalDateTime dinnerTime = effectiveStart.plusMinutes(10);
                    if (dinnerTime.plusMinutes(DEFAULT_MEAL_MINUTES).isBefore(sleepStart)) {
                        slots.add(new BaseSlotDto(formatIso(dinnerTime), ActivityType.MEAL, DEFAULT_MEAL_MINUTES, "귀가 후 식사 및 휴식", null));
                    }

                    slots.add(new BaseSlotDto(formatIso(sleepStart), ActivityType.SLEEP, sleepMinutes, "취침", "권장 수면: " + (sleepMinutes / 60) + "시간 " + (sleepMinutes % 60) + "분"));
                    slots.add(new BaseSlotDto(formatIso(wakeTime), ActivityType.WAKE_UP, 15L, "기상", null));

                    // 기상 후 출근 준비(prepTime)까지 3시간 이상 여유가 있는 경우
                    if (prepTime != null && Duration.between(wakeTime, prepTime).toHours() >= 3) {
                        LocalDateTime lunchTime = prepTime.minusHours(1).minusMinutes(30);
                        slots.add(new BaseSlotDto(formatIso(lunchTime), ActivityType.MEAL, MAX_MEAL_MINUTES, "점심 식사", "출근 전 든든한 식사"));
                        flexIntervals.add(formatIso(wakeTime.plusMinutes(30)) + " ~ " + formatIso(lunchTime.minusMinutes(15)) + " (오전/낮 여유 시간)");
                    }
                } else if (availableSleep >= 40) {
                    // 가용 시간이 매우 짧은 경우 (예: 2.5시간 쉼) -> 쪽잠(NAP)으로 처리
                    long napMinutes = Math.min(DEFAULT_NAP_MINUTES, availableSleep - 10);
                    slots.add(new BaseSlotDto(formatIso(sleepStart), ActivityType.NAP, napMinutes, "단기 쪽잠 및 휴식", "빠른 피로 회복을 위한 쪽잠"));
                    slots.add(new BaseSlotDto(formatIso(sleepStart.plusMinutes(napMinutes)), ActivityType.WAKE_UP, 10L, "기상 및 출근 준비", null));
                } else {
                    // 40분 미만 초단축: 수면 0시간, 휴식만 배치
                    slots.add(new BaseSlotDto(formatIso(effectiveStart), ActivityType.REST, Math.max(10, totalFreeMinutes - 40), "가벼운 휴식 및 이동", "출근 전 스트레칭 및 에너지 보충"));
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
