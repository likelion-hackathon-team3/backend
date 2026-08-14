package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// Analysis에서 각 Calculator가 공통으로 사용할 referenceDate(근무표 기준 날짜)를 결정하는 순수 계산 클래스.
// DB/Repository에 의존하지 않고, 이미 조회된 데이터(근무표 Map, Environment)와 현재 시각만 입력으로 받는다.
//
// referenceDate의 목적:
// - "지금 실제로 진행 중인 근무가 어느 Schedule.date(roster/work date)에 배정된 근무인가"를 판단한다.
// - NIGHT 00:00처럼 실제 근무가 다음 calendar date로 넘어가는 경우, now의 날짜가 아니라
//   그 근무가 배정된 근무표 기준 날짜를 referenceDate로 써야 한다.
//
// 규칙(확정됨):
//   currentDate  = now.toLocalDate()
//   previousDate = currentDate - 1일
//   기본값: referenceDate = currentDate
//   단, previousDate에 실제 근무(DAY/EVENING/NIGHT)가 있고,
//       그 근무의 실제 구간 [actualStart, actualEnd) 안에 now가 있으면(진행 중)
//       referenceDate = previousDate.
//   OFF/미등록은 실제 근무가 아니므로 진행 중 판정 대상에서 제외한다.
//
// 진행 중 구간은 반열림 [actualStart, actualEnd):
//   now == actualStart -> 진행 중(전날)
//   now == actualEnd   -> 이미 종료(오늘)
//
// 근무의 실제 시작/종료 날짜 계산은 새로 구현하지 않고 ShiftDateTimeResolver를 재사용한다.
public class ReferenceDateResolver {

    // 근무표 기준 날짜 + Environment로 "실제 시작/종료 LocalDateTime"을 계산하는 순수 헬퍼.
    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다(미등록).
    // environment: 실제 근무 시간대(정상적인 값이 전달된다는 전제. null fallback을 두지 않는다).
    // now: 현재 시각(주입). LocalDateTime.now()를 내부에서 직접 호출하지 않는다.
    public LocalDate resolve(Map<LocalDate, ShiftType> schedules,
                             Environment environment,
                             LocalDateTime now) {
        LocalDate currentDate = now.toLocalDate();
        LocalDate previousDate = currentDate.minusDays(1);

        ShiftType previousShift = schedules.get(previousDate); // 미등록이면 null

        // 전날이 실제 근무(DAY/EVENING/NIGHT)이고, 그 실제 구간 안에 now가 있으면 전날을 기준으로 삼는다.
        if (isWork(previousShift)) {
            LocalDateTime start = resolver.actualStart(previousDate, previousShift, environment);
            LocalDateTime end = resolver.actualEnd(previousDate, previousShift, environment);

            // 반열림 [start, end): start는 포함, end는 제외.
            boolean inProgress = !now.isBefore(start) && now.isBefore(end);
            if (inProgress) {
                return previousDate;
            }
        }

        return currentDate;
    }

    // DAY/EVENING/NIGHT면 실제 근무. null(미등록)/OFF면 근무 아님.
    private boolean isWork(ShiftType shift) {
        return shift == ShiftType.DAY || shift == ShiftType.EVENING || shift == ShiftType.NIGHT;
    }
}
