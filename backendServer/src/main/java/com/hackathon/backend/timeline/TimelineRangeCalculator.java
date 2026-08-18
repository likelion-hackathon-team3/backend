package com.hackathon.backend.timeline;

import com.hackathon.backend.analysis.ShiftDateTimeResolver;
import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 날짜별 전환(currentShift on date, nextShift on date+1) 하나만 보고
// timelineStart/timelineEnd 범위를 계산하는 순수 계산 클래스. DB에 의존하지 않는다.
//
// D/E/N 실제 시작/종료는 기존 ShiftDateTimeResolver를 그대로 재사용하고,
// NIGHT roster-date 규칙을 여기서 다시 구현하지 않는다.
//
// 4개 그룹 + WORK_TO_OFF의 currentShift(DAY/EVENING vs NIGHT) 분기만 둔다(확정 규칙):
// - WORK_TO_WORK      : actualEnd(date,current)+commute ~ actualStart(date+1,next)-commute
// - WORK_TO_OFF(D/E)  : actualEnd(date,current)+commute ~ 그 시각 이후 처음 도래하는 07:00
// - WORK_TO_OFF(NIGHT): NIGHT.actualEnd+commute ~ (그 시각 + 12시간) 고정 회복 window
// - OFF_TO_WORK       : date 07:00(고정 앵커) ~ actualStart(date+1,next)-commute
// - OFF_TO_OFF        : date 07:00 ~ date+1 07:00 (고정 하루 템플릿 앵커)
public class TimelineRangeCalculator {

    private static final LocalTime MORNING_ANCHOR = LocalTime.of(7, 0);
    private static final int NIGHT_RECOVERY_WINDOW_HOURS = 12;

    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();

    TimelineRange calculate(LocalDate date, ShiftType currentShift, ShiftType nextShift, Environment environment) {
        boolean currentWork = isWork(currentShift);
        boolean nextWork = isWork(nextShift);

        if (currentWork && nextWork) {
            return workToWork(date, currentShift, nextShift, environment);
        }
        if (currentWork) {
            return workToOff(date, currentShift, environment);
        }
        if (nextWork) {
            return offToWork(date, nextShift, environment);
        }
        return offToOff(date);
    }

    private TimelineRange workToWork(LocalDate date, ShiftType currentShift, ShiftType nextShift, Environment environment) {
        LocalDateTime start = resolver.actualEnd(date, currentShift, environment)
                .plusMinutes(environment.getCommuteMinutes());
        LocalDateTime nextActualStart = resolver.actualStart(date.plusDays(1), nextShift, environment);
        LocalDateTime end = nextActualStart.minusMinutes(environment.getCommuteMinutes());
        return new TimelineRange(TimelineGroup.WORK_TO_WORK, start, end, nextActualStart);
    }

    private TimelineRange workToOff(LocalDate date, ShiftType currentShift, Environment environment) {
        LocalDateTime start = resolver.actualEnd(date, currentShift, environment)
                .plusMinutes(environment.getCommuteMinutes());
        LocalDateTime end = currentShift == ShiftType.NIGHT
                ? start.plusHours(NIGHT_RECOVERY_WINDOW_HOURS)
                : nextMorningAnchor(start);
        return new TimelineRange(TimelineGroup.WORK_TO_OFF, start, end, null);
    }

    private TimelineRange offToWork(LocalDate date, ShiftType nextShift, Environment environment) {
        LocalDateTime start = date.atTime(MORNING_ANCHOR);
        LocalDateTime nextActualStart = resolver.actualStart(date.plusDays(1), nextShift, environment);
        LocalDateTime end = nextActualStart.minusMinutes(environment.getCommuteMinutes());
        return new TimelineRange(TimelineGroup.OFF_TO_WORK, start, end, nextActualStart);
    }

    private TimelineRange offToOff(LocalDate date) {
        LocalDateTime start = date.atTime(MORNING_ANCHOR);
        LocalDateTime end = date.plusDays(1).atTime(MORNING_ANCHOR);
        return new TimelineRange(TimelineGroup.OFF_TO_OFF, start, end, null);
    }

    // start 시각 "이후" 처음 도래하는 07:00.
    // start < 07:00 -> 같은 날 07:00, start >= 07:00(정각 포함) -> 다음날 07:00.
    // 정각을 포함시키는 이유: "timelineStart 이후 처음 도래하는 07:00"이 timelineStart 자신과
    // 같아져서 usable window가 0이 되는 것을 막기 위함이다.
    private LocalDateTime nextMorningAnchor(LocalDateTime start) {
        LocalDateTime sameDayAnchor = start.toLocalDate().atTime(MORNING_ANCHOR);
        return start.isBefore(sameDayAnchor) ? sameDayAnchor : sameDayAnchor.plusDays(1);
    }

    private boolean isWork(ShiftType shift) {
        return shift == ShiftType.DAY || shift == ShiftType.EVENING || shift == ShiftType.NIGHT;
    }
}
