package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 근무표 기준 날짜(roster/work date)와 Environment의 시간대를 이용해
// 근무의 "실제 LocalDateTime(시작/종료)"를 계산하는 순수 계산 클래스. DB에 의존하지 않는다.
//
// 배경(확정된 기획):
// - Schedule.date는 실제 calendar 시작일이 아니라, D/E/N/OFF가 배정된 근무표 기준 날짜다.
// - 하나의 근무표 기준일 안에서 시간대는 DAY -> EVENING -> NIGHT 순서로 해석한다.
// - 그래서 같은 "8/19 NIGHT"라도 병원의 NIGHT 시작시간에 따라 실제 시작 날짜가 달라질 수 있다.
//
// 실제 시작 날짜 규칙:
// - DAY / EVENING : scheduleDate 당일에 시작.
// - NIGHT :
//     nightStart >= eveningStart  -> scheduleDate 당일 시작(동률도 당일).
//     nightStart <  eveningStart  -> 자정을 지난 것으로 보고 scheduleDate + 1일에 시작.
//
// 실제 종료 규칙(실제 시작을 먼저 구한 뒤):
// - endTime >  startTime  -> 실제 시작 날짜에 종료.
// - endTime <= startTime  -> 자정을 넘긴 것으로 보고 실제 시작 날짜 + 1일에 종료.
public class ShiftDateTimeResolver {

    // 근무의 실제 시작 LocalDateTime.
    public LocalDateTime actualStart(LocalDate scheduleDate, ShiftType shift, Environment env) {
        LocalTime start = startTimeOf(shift, env);

        // NIGHT만 시작 날짜가 다음날로 밀릴 수 있다. DAY/EVENING은 항상 scheduleDate 당일.
        if (shift == ShiftType.NIGHT && start.isBefore(env.getEveningStart())) {
            return LocalDateTime.of(scheduleDate.plusDays(1), start);
        }
        return LocalDateTime.of(scheduleDate, start);
    }

    // 근무의 실제 종료 LocalDateTime. 먼저 actualStart를 구한 뒤 종료 날짜를 정한다.
    public LocalDateTime actualEnd(LocalDate scheduleDate, ShiftType shift, Environment env) {
        LocalDateTime startLdt = actualStart(scheduleDate, shift, env);
        LocalTime start = startLdt.toLocalTime();
        LocalTime end = endTimeOf(shift, env);

        // 종료가 시작보다 늦으면 같은 날, 같거나 이르면 자정을 넘긴 것으로 보고 하루 뒤.
        if (end.isAfter(start)) {
            return LocalDateTime.of(startLdt.toLocalDate(), end);
        }
        return LocalDateTime.of(startLdt.toLocalDate().plusDays(1), end);
    }

    private LocalTime startTimeOf(ShiftType shift, Environment env) {
        return switch (shift) {
            case DAY -> env.getDayStart();
            case EVENING -> env.getEveningStart();
            case NIGHT -> env.getNightStart();
            case OFF -> throw new IllegalArgumentException("OFF는 시작시각이 없습니다.");
        };
    }

    private LocalTime endTimeOf(ShiftType shift, Environment env) {
        return switch (shift) {
            case DAY -> env.getDayEnd();
            case EVENING -> env.getEveningEnd();
            case NIGHT -> env.getNightEnd();
            case OFF -> throw new IllegalArgumentException("OFF는 종료시각이 없습니다.");
        };
    }
}
