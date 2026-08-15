package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

// 위험도 계산용 "현재 시점 기준 위험 패턴"(WorkTransitionPattern)을 찾는 순수 계산 클래스.
// DB에 의존하지 않고, 이미 조회된 데이터(근무표 Map, Environment)와 referenceDate/now만 입력으로 받는다.
//
// 목적:
// N -> OFF -> D 처럼 두 실제 근무 사이에 며칠의 휴무(OFF/미등록)가 끼어 있는지까지
// 포함한 "위험 평가 대상 패턴"을 일반화해서 찾는다.
// TransitionCalculator(바로 다음 날 하나만 비교)와는 별개 계산이며, 기존 계산기는 수정하지 않는다.
//
// 판단 기준:
// - referenceDate에 배정된 근무가 DAY/EVENING/NIGHT이고 이미 시작했으면(now >= actualStart)
//   그 근무 자체가 previousWorkShift가 되고, 그 이후에서 다음 실제 근무를 찾는다.
// - 그 외(OFF/미등록이거나, 아직 시작 전인 근무)에는
//   referenceDate보다 이전에서 가장 가까운 실제 근무를 previousWorkShift로,
//   referenceDate 이상(자기 자신 포함)에서 가장 가까운 실제 근무를 nextWorkShift로 찾는다.
//   (아직 시작 안 한 오늘 근무 자신이 nextWorkShift가 될 수 있다.)
// - 이전 실제 근무가 없으면 previousWorkShift/Date = null, offDaysBetween = 0으로 저장한다.
//   (0은 직결 패턴이 아니라 "계산 대상 아님"을 뜻하며, PatternRiskCalculator가 null을 먼저 걸러 0점 처리한다.)
// - 다음 실제 근무를 찾지 못하면 Optional.empty()를 반환한다.
// - 근무표를 하루씩 순회하지 않고, 실제 근무(DAY/EVENING/NIGHT) 날짜만 모은 TreeSet의
//   floor/lower/ceiling/higher 연산으로 찾는다. 등록 기간이 몇 달이어도 빠르게 동작한다.
public class ShiftPatternCalculator {

    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다(미등록).
    // environment: actualStart 계산에 사용.
    // referenceDate: ReferenceDateResolver가 결정한 근무표 기준 날짜.
    // now: 현재 시각(주입).
    public Optional<WorkTransitionPattern> find(Map<LocalDate, ShiftType> schedules,
                                                Environment environment,
                                                LocalDate referenceDate,
                                                LocalDateTime now) {
        TreeSet<LocalDate> workDates = new TreeSet<>();
        for (Map.Entry<LocalDate, ShiftType> entry : schedules.entrySet()) {
            if (isWork(entry.getValue())) {
                workDates.add(entry.getKey());
            }
        }

        ShiftType referenceShift = schedules.get(referenceDate);
        boolean referenceStarted = isWork(referenceShift)
                && !now.isBefore(resolver.actualStart(referenceDate, referenceShift, environment));

        LocalDate previousWorkDate;
        LocalDate nextWorkDate;

        if (referenceStarted) {
            previousWorkDate = referenceDate;
            nextWorkDate = workDates.higher(referenceDate);
        } else {
            previousWorkDate = workDates.lower(referenceDate);
            nextWorkDate = workDates.ceiling(referenceDate);

            // NIGHT roster-date 규칙 때문에 previousWorkDate로 잡힌 근무가 사실은
            // "아직 시작 안 한 다음 근무"인 경우가 있다.
            // 예: NIGHT 01:37 시작 -> referenceDate가 다음날로 넘어갔지만 그 NIGHT는 아직 시작 전.
            // NextShiftMinutesCalculator와 동일한 기준(actualStart.isAfter(now), 정각은 제외)으로 재분류한다.
            if (previousWorkDate != null && schedules.get(previousWorkDate) == ShiftType.NIGHT) {
                LocalDateTime nightActualStart =
                        resolver.actualStart(previousWorkDate, ShiftType.NIGHT, environment);
                if (nightActualStart.isAfter(now)) {
                    nextWorkDate = previousWorkDate;
                    previousWorkDate = workDates.lower(previousWorkDate);
                }
            }
        }

        if (nextWorkDate == null) {
            return Optional.empty();
        }

        if (previousWorkDate == null) {
            return Optional.of(new WorkTransitionPattern(
                    null, null,
                    schedules.get(nextWorkDate), nextWorkDate,
                    0
            ));
        }

        int offDaysBetween = (int) ChronoUnit.DAYS.between(previousWorkDate, nextWorkDate) - 1;

        return Optional.of(new WorkTransitionPattern(
                schedules.get(previousWorkDate), previousWorkDate,
                schedules.get(nextWorkDate), nextWorkDate,
                offDaysBetween
        ));
    }

    // DAY/EVENING/NIGHT면 실제 근무. OFF/미등록(null)은 근무 아님.
    private boolean isWork(ShiftType shift) {
        return shift == ShiftType.DAY || shift == ShiftType.EVENING || shift == ShiftType.NIGHT;
    }
}
