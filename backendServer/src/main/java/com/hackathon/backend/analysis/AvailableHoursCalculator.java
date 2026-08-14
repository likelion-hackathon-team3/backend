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
// - availableHours = 시작점 ~ "다음 실제 근무 시작시각" 사이의 시간에서 통근시간을 뺀 값(시간, 소수).
// - 시작점:
//     referenceDate가 근무일이면 그 근무의 실제 종료시각(LocalDateTime, 자정 넘김 반영),
//     referenceDate가 OFF/미등록이면 referenceTime(현재 시각).
//       단 전날 NIGHT가 referenceDate까지 이어져 그 실제 종료가 referenceTime보다 늦으면
//       그 NIGHT 실제 종료시각을 시작점으로 쓴다. => max(referenceTime, 전날 NIGHT 종료).
// - 종료점: OFF/미등록을 건너뛰고 만나는 "다음 실제 근무"의 시작시각.
//     (이 건너뛰기는 시간 계산에서만 쓰며 transitionType 규칙에는 영향을 주지 않는다.)
// - 통근 차감(귀가 통근은 "시작점 이후에 아직 귀가가 남아 있는가"로 판단):
//     현재가 근무일 -> 귀가 1회 + 다음 출근 1회 = 2회
//     OFF/미등록이고 전날 NIGHT 영향 없음 -> 다음 출근 1회
//     OFF/미등록이지만 전날 NIGHT가 아직 진행 중(그 종료시각을 시작점으로 사용) -> 귀가 1회 + 다음 출근 1회 = 2회
// - 준비시간은 차감하지 않는다.
// - 통근 차감 후 음수면 0.0으로 처리한다.
// - 반올림하지 않고 원본 double을 반환한다(표시용 반올림은 나중 Response 조립에서).
// - 미래에 실제 근무가 하나도 없으면 OptionalDouble.empty()를 반환한다.
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

        // 1) 다음 실제 근무(DAY/EVENING/NIGHT) 날짜 찾기. 없으면 계산 불가 → empty.
        Optional<LocalDate> nextWorkDate = findNextWorkDate(schedules, referenceDate);
        if (nextWorkDate.isEmpty()) {
            return OptionalDouble.empty();
        }

        // 2) 시작점 계산 + 귀가 통근이 남아 있는지 판단
        ShiftType currentShift = schedules.get(referenceDate); // 미등록이면 null
        boolean currentIsWork = isWork(currentShift);

        LocalDateTime startLdt;
        boolean hasReturnCommute; // 시작점 이후에 아직 귀가 통근이 남아 있는가
        if (currentIsWork) {
            // 현재 근무의 실제 종료시각(NIGHT 등 자정 넘김 반영). 종료 후 귀가가 남아 있다.
            startLdt = resolver.actualEnd(referenceDate, currentShift, environment);
            hasReturnCommute = true;
        } else {
            // OFF/미등록: 기본은 현재 시각이고 귀가할 근무가 없다.
            startLdt = referenceTime;
            hasReturnCommute = false;

            // 단 전날 NIGHT가 referenceTime 시점에도 아직 안 끝났다면,
            // 그 NIGHT 종료시각을 시작점으로 쓰고 NIGHT 퇴근 후 귀가도 아직 남아 있다.
            ShiftType prevShift = schedules.get(referenceDate.minusDays(1));
            if (prevShift == ShiftType.NIGHT) {
                LocalDateTime prevNightEnd =
                        resolver.actualEnd(referenceDate.minusDays(1), ShiftType.NIGHT, environment);
                if (prevNightEnd.isAfter(startLdt)) {
                    startLdt = prevNightEnd;
                    hasReturnCommute = true;
                }
            }
        }

        // 3) 종료점 = 다음 실제 근무의 실제 시작시각(NIGHT 00:00처럼 다음날 시작도 반영)
        ShiftType nextShift = schedules.get(nextWorkDate.get());
        LocalDateTime endLdt = resolver.actualStart(nextWorkDate.get(), nextShift, environment);

        // 4) 통근 차감: (남아 있는 귀가 통근이면 1회) + 다음 실제 근무 출근 1회
        int commuteCount = (hasReturnCommute ? 1 : 0) + 1;
        long commuteMinutes = (long) commuteCount * environment.getCommuteMinutes();

        // 5) 전체분 - 통근차감분, 음수면 0으로 자름
        long totalMinutes = Duration.between(startLdt, endLdt).toMinutes();
        long availableMinutes = Math.max(0, totalMinutes - commuteMinutes);

        // 반올림하지 않고 원본 double(시간)로 반환
        return OptionalDouble.of(availableMinutes / 60.0);
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
