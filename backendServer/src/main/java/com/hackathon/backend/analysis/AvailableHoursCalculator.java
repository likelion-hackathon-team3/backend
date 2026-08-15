package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

// 실제 활용 가능시간(availableHours)만 계산하는 순수 계산 클래스.
// DB에 직접 접근하지 않고, 이미 조회된 데이터(근무표 Map, Environment)만 입력으로 받는다.
// 시간 계산은 문자열이 아니라 Java Time API(LocalDate/LocalTime/LocalDateTime/Duration)만 사용한다.
//
// 규칙(확정됨):
// - Analysis 계산상 DAY/EVENING/NIGHT = 근무 있음, OFF/미등록 = 근무 없음(동일 취급).
// - referenceDate에 실제 근무(DAY/EVENING/NIGHT)가 배정돼 있으면, referenceTime이
//   그 근무의 실제 구간 [actualStart, actualEnd) 어디에 있는지에 따라 3가지로 나눠 계산한다.
//   (actualStart/actualEnd는 새로 계산하지 않고 ShiftDateTimeResolver를 그대로 재사용한다.)
//
//   [Branch A] referenceTime < actualStart : 그 근무가 아직 시작 전 → 그 근무 자체가 "다음 근무"다.
//       구간 = referenceTime ~ actualStart. 출근 통근만 1회 차감. (아직 시작 안 한 근무를
//       "이미 끝난 근무"로 보고 그 이후 근무까지 건너뛰지 않는다.)
//   [Branch B] actualStart <= referenceTime < actualEnd : 지금 근무 중.
//       구간 = actualEnd ~ 다음 실제 근무 시작. 귀가 1회 + 출근 1회 = 통근 2회 차감.
//   [Branch C] referenceTime >= actualEnd : 그 근무는 이미 종료됨.
//       구간 = referenceTime ~ 다음 실제 근무 시작. 이미 지나간 귀가는 빼지 않고 출근 1회만 차감.
//
// - referenceDate가 OFF/미등록이면 위 3분기와 무관하게 기존 규칙을 그대로 쓴다:
//     시작점 = referenceTime. 단 전날 NIGHT가 referenceTime 시점에도 아직 안 끝났다면
//     그 NIGHT 실제 종료시각을 시작점으로 쓰고 귀가 통근도 1회 추가(통근 2회), 아니면 출근 1회만.
// - "다음 실제 근무"는 OFF/미등록을 건너뛰고 찾으며, 이 건너뛰기는 시간 계산에서만 쓰고
//   transitionType 규칙에는 영향을 주지 않는다.
// - 준비시간은 차감하지 않는다.
// - 통근 차감 후 음수면 0.0으로 처리한다.
// - 반올림하지 않고 원본 double을 반환한다(표시용 반올림은 나중 Response 조립에서).
// - 다음 실제 근무를 찾지 못하면 OptionalDouble.empty()를 반환한다.
//   (Branch A는 예외: 다음 근무가 referenceDate 자신의 근무이므로 항상 값이 있다.)
public class AvailableHoursCalculator {

    // 근무표 기준 날짜 + Environment로 "실제 시작/종료 LocalDateTime"을 계산하는 순수 헬퍼.
    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다(미등록).
    // environment: D/E/N 시작·종료시각과 commuteMinutes(평균 편도 통근시간, 분).
    // referenceDate: 현재/기준 날짜.
    // referenceTime: "현재 시각". 실서비스에선 now()를 주입한다(테스트를 위해 인자로 받는다).
    public OptionalDouble calculate(Map<LocalDate, ShiftType> schedules,
                                    Environment environment,
                                    LocalDate referenceDate,
                                    LocalDateTime referenceTime) {

        ShiftType currentShift = schedules.get(referenceDate); // 미등록이면 null
        boolean currentIsWork = isWork(currentShift);

        LocalDateTime startLdt;
        LocalDateTime endLdt;
        int commuteCount;

        if (currentIsWork) {
            LocalDateTime currentActualStart = resolver.actualStart(referenceDate, currentShift, environment);
            LocalDateTime currentActualEnd = resolver.actualEnd(referenceDate, currentShift, environment);

            if (referenceTime.isBefore(currentActualStart)) {
                // Branch A: 아직 시작 전 → 그 근무 자체가 다음 근무. 미래 탐색 불필요, 항상 값이 있다.
                startLdt = referenceTime;
                endLdt = currentActualStart;
                commuteCount = 1;

            } else if (referenceTime.isBefore(currentActualEnd)) {
                // Branch B: 근무 중. 종료 후 귀가 + 다음 근무 출근 = 통근 2회.
                Optional<LocalDateTime> nextStart = nextWorkActualStart(schedules, referenceDate, environment);
                if (nextStart.isEmpty()) {
                    return OptionalDouble.empty();
                }
                startLdt = currentActualEnd;
                endLdt = nextStart.get();
                commuteCount = 2;

            } else {
                // Branch C: 이미 종료됨. 이미 지나간 귀가는 빼지 않고 다음 근무 출근 1회만.
                Optional<LocalDateTime> nextStart = nextWorkActualStart(schedules, referenceDate, environment);
                if (nextStart.isEmpty()) {
                    return OptionalDouble.empty();
                }
                startLdt = referenceTime;
                endLdt = nextStart.get();
                commuteCount = 1;
            }

        } else {
            // OFF/미등록.
            LocalDate prevDate = referenceDate.minusDays(1);
            ShiftType prevShift = schedules.get(prevDate);

            // 전날이 NIGHT인데 roster-date 규칙 때문에 실제로는 아직 시작 안 한 경우인지 먼저 확인한다.
            // 예: NIGHT 01:37 시작 -> referenceDate가 다음날로 넘어갔지만 실제 시작 전인 상태.
            // 이때는 그 NIGHT 자체가 "다음 근무"이며 Branch A와 동일하게 출근 통근 1회만 차감한다.
            // NextShiftMinutesCalculator와 동일한 기준(actualStart.isAfter(referenceTime), 정각은 제외)으로 판단한다.
            LocalDateTime prevNightActualStart = prevShift == ShiftType.NIGHT
                    ? resolver.actualStart(prevDate, ShiftType.NIGHT, environment)
                    : null;

            if (prevNightActualStart != null && prevNightActualStart.isAfter(referenceTime)) {
                startLdt = referenceTime;
                endLdt = prevNightActualStart;
                commuteCount = 1;

            } else {
                // 기존 규칙 그대로 유지.
                Optional<LocalDateTime> nextStart = nextWorkActualStart(schedules, referenceDate, environment);
                if (nextStart.isEmpty()) {
                    return OptionalDouble.empty();
                }

                startLdt = referenceTime;
                boolean hasReturnCommute = false;

                // 전날 NIGHT가 referenceTime 시점에도 아직 안 끝났다면,
                // 그 NIGHT 종료시각을 시작점으로 쓰고 NIGHT 퇴근 후 귀가도 아직 남아 있다.
                if (prevShift == ShiftType.NIGHT) {
                    LocalDateTime prevNightEnd = resolver.actualEnd(prevDate, ShiftType.NIGHT, environment);
                    if (prevNightEnd.isAfter(startLdt)) {
                        startLdt = prevNightEnd;
                        hasReturnCommute = true;
                    }
                }

                endLdt = nextStart.get();
                commuteCount = (hasReturnCommute ? 1 : 0) + 1;
            }
        }

        long commuteMinutes = (long) commuteCount * environment.getCommuteMinutes();
        long totalMinutes = Duration.between(startLdt, endLdt).toMinutes();
        long availableMinutes = Math.max(0, totalMinutes - commuteMinutes);

        // 반올림하지 않고 원본 double(시간)로 반환
        return OptionalDouble.of(availableMinutes / 60.0);
    }

    // "다음 실제 근무"의 실제 시작 LocalDateTime. 없으면 empty.
    // (findNextWorkDate + actualStart 조회를 Branch B/C/OFF 3곳에서 공통으로 쓰기 위한 헬퍼.)
    private Optional<LocalDateTime> nextWorkActualStart(Map<LocalDate, ShiftType> schedules,
                                                        LocalDate referenceDate,
                                                        Environment environment) {
        Optional<LocalDate> nextWorkDate = findNextWorkDate(schedules, referenceDate);
        return nextWorkDate.map(date -> resolver.actualStart(date, schedules.get(date), environment));
    }

    // referenceDate 다음 날부터 미래로 이동하며 OFF/미등록을 건너뛰고 처음 만나는 실제 근무 날짜.
    // 탐색은 저장된 근무표의 가장 늦은 날짜까지만 한다. 못 찾으면 empty.
    private Optional<LocalDate> findNextWorkDate(Map<LocalDate, ShiftType> schedules,
                                                LocalDate referenceDate) {
        if (schedules.isEmpty()) {
            return Optional.empty();
        }
        LocalDate maxDate = schedules.keySet().stream().max(LocalDate::compareTo).get();

        LocalDate date = referenceDate.plusDays(1);
        while (!date.isAfter(maxDate)) {
            if (isWork(schedules.get(date))) {
                return Optional.of(date);
            }
            date = date.plusDays(1);
        }
        return Optional.empty();
    }

    // DAY/EVENING/NIGHT면 근무일. null(미등록)/OFF면 근무 아님.
    private boolean isWork(ShiftType shift) {
        return shift == ShiftType.DAY || shift == ShiftType.EVENING || shift == ShiftType.NIGHT;
    }
}
