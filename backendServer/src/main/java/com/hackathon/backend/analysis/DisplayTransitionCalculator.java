package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

// 화면에 보여줄 transitionType(예: OFF_TO_DAY, DAY_TO_NIGHT, NIGHT_TO_OFF)을 계산하는 순수 계산 클래스.
// risk 계산용 WorkTransitionPattern/TransitionCalculator(근무표상 다음날 단순 비교)와는 다른 개념이며,
// 이 계산기의 결과는 risk 계산에 다시 연결하지 않는다.
//
// 규칙(확정됨):
// - referenceDate의 근무가 이미 시작됐으면(now >= actualStart, actualEnd는 보지 않음)
//   그 근무 -> referenceDate+1일의 근무(OFF 포함) 그대로 보여준다.
//   -> 기존 TransitionCalculator.calculate(schedules, referenceDate)와 완전히 동일하므로 그대로 재사용한다.
// - 아직 시작 안 했으면(OFF/미등록이거나, D/E/N이지만 actualStart 전)
//   다음 예정된 실제 근무(D/E/N)로 들어가는 전환을 보여준다.
//   그 직전 상태는 pattern.previousWorkShift()가 있고 offDaysBetween()==0(휴무 없이 바로 이어짐)일 때만
//   그 근무를 이월해서 보여주고, 그 외(휴무가 있거나 이전 실제 근무가 없음)에는 OFF로 보여준다.
//   다음 근무는 pattern.nextWorkShift()를 그대로 쓴다(NIGHT roster-date 간극도 이미 반영된 값).
//
// pattern은 이미 AnalysisService가 위험도 계산을 위해 ShiftPatternCalculator로 구한 것을 그대로 받는다
// (여기서 다시 계산하지 않는다. WorkTransitionPattern의 의미도 바꾸지 않는다).
public class DisplayTransitionCalculator {

    private final ShiftDateTimeResolver resolver = new ShiftDateTimeResolver();
    private final TransitionCalculator transitionCalculator = new TransitionCalculator();

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다(미등록).
    // environment: actualStart 계산에 사용.
    // referenceDate: ReferenceDateResolver가 결정한 근무표 기준 날짜.
    // now: 현재 시각(주입).
    // pattern: ShiftPatternCalculator가 이미 계산한 risk용 패턴(다음 실제 근무 없으면 이 계산기는 호출하지 않는다).
    public String calculate(Map<LocalDate, ShiftType> schedules,
                            Environment environment,
                            LocalDate referenceDate,
                            LocalDateTime now,
                            WorkTransitionPattern pattern) {
        ShiftType referenceShift = schedules.get(referenceDate);
        boolean started = isWork(referenceShift)
                && !now.isBefore(resolver.actualStart(referenceDate, referenceShift, environment));

        if (started) {
            return transitionCalculator.calculate(schedules, referenceDate);
        }

        ShiftType current = (pattern.previousWorkShift() != null && pattern.offDaysBetween() == 0)
                ? pattern.previousWorkShift()
                : ShiftType.OFF;

        return current.name() + "_TO_" + pattern.nextWorkShift().name();
    }

    // DAY/EVENING/NIGHT면 실제 근무. OFF/미등록(null)은 근무 아님.
    private boolean isWork(ShiftType shift) {
        return shift == ShiftType.DAY || shift == ShiftType.EVENING || shift == ShiftType.NIGHT;
    }
}
