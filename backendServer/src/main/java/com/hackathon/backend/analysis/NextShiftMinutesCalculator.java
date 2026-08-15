package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.OptionalLong;

// "다음 근무까지 남은 시간(nextShiftMinutes)"만 계산하는 순수 계산 클래스.
// DB에 직접 접근하지 않고, 이미 조회된 데이터(근무표 Map, Environment)와 현재 시각만 입력으로 받는다.
//
// 규칙(확정됨):
// - 시작점: 현재 시각(now). (통근/준비시간 차감 없음. 현재 근무 종료시각을 쓰지 않는다.)
//   → availableHours와는 의미가 다르다.
// - 종료점: now 이후 가장 먼저 "시작"하는 실제 근무(DAY/EVENING/NIGHT)의 실제 시작시각.
// - 실제 시작시각은 새로 계산하지 않고 ShiftDateTimeResolver.actualStart(...)를 재사용한다
//   (NIGHT 00:00/23:00 등 근무표 기준 날짜 규칙을 그대로 따른다).
// - 후보 조건은 actualStart > now (엄격히 큼).
//   → 이미 진행 중이거나(actualStart <= now) now와 정확히 같은 시작시각의 근무는 "다음 근무"가 아니다.
// - OFF/미등록 날짜는 실제 근무가 아니므로 후보에서 제외한다.
// - 탐색은 저장된 근무표(schedules) 안의 근무 entry로 한정한다(무한 미래 탐색 없음).
// - 여러 후보가 있으면 scheduleDate 순서가 아니라 actualStart가 가장 이른(가장 가까운) 근무를 고른다.
// - 결과는 Duration.between(now, actualStart).toMinutes() 그대로(분 미만은 버림).
// - now 이후 실제 근무가 하나도 없으면 OptionalLong.empty()를 반환한다.
public class NextShiftMinutesCalculator {

    // 근무표 기준 날짜 + Environment로 "실제 시작 LocalDateTime"을 계산하는 순수 헬퍼.
    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다(미등록).
    // environment: D/E/N 시작·종료시각(actualStart 계산에 사용).
    // now: 현재 시각(주입). LocalDateTime.now()를 내부에서 직접 호출하지 않는다.
    public OptionalLong calculate(Map<LocalDate, ShiftType> schedules,
                                  Environment environment,
                                  LocalDateTime now) {

        // now 이후 가장 이른 실제 근무 시작시각을 찾는다(actualStart > now 후보 중 최소).
        LocalDateTime earliestNextStart = null;

        for (Map.Entry<LocalDate, ShiftType> entry : schedules.entrySet()) {
            ShiftType shift = entry.getValue();
            if (!isWork(shift)) {
                continue; // OFF/미등록(값 없음)은 실제 근무가 아니므로 건너뜀
            }

            LocalDateTime actualStart = resolver.actualStart(entry.getKey(), shift, environment);

            // 이미 시작했거나(<= now) now와 같은 시작시각은 "다음 근무"가 아니다.
            if (!actualStart.isAfter(now)) {
                continue;
            }

            // now 이후 후보 중 가장 이른 시작시각을 유지한다.
            if (earliestNextStart == null || actualStart.isBefore(earliestNextStart)) {
                earliestNextStart = actualStart;
            }
        }

        if (earliestNextStart == null) {
            return OptionalLong.empty();
        }

        // 분 미만은 버림(Duration.toMinutes() 기본 동작). 별도 반올림/올림 없음.
        return OptionalLong.of(Duration.between(now, earliestNextStart).toMinutes());
    }

    // DAY/EVENING/NIGHT면 실제 근무. OFF면 근무 아님.
    private boolean isWork(ShiftType shift) {
        return shift == ShiftType.DAY || shift == ShiftType.EVENING || shift == ShiftType.NIGHT;
    }
}
