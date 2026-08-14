package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.util.Map;

// 전환유형(transitionType)만 계산하는 순수 계산 클래스.
// DB에 직접 접근하지 않고, 이미 조회된 "날짜 -> 근무유형" 데이터만 입력으로 받는다.
// 그래서 DB 없이도 단위 테스트가 쉽고, 나중에 AnalysisService가 그대로 호출할 수 있다.
//
// 규칙(확정됨):
// - 기준일(referenceDate)의 근무와 그 "다음 날(referenceDate + 1일)"의 근무를 비교한다.
// - 해당 날짜에 저장된 Schedule이 없으면 계산상 OFF로 취급한다(implicit OFF).
//   (DB에 OFF를 새로 만들지 않는다. 여기서는 값만 OFF로 본다.)
// - 미등록 날짜를 건너뛰고 과거 근무와 미래 근무를 직접 연결하지 않는다.
//   즉 항상 "바로 다음 날"만 본다.
// - transitionType은 약어가 아니라 풀네임을 쓴다. 예: EVENING_TO_DAY, NIGHT_TO_OFF.
public class TransitionCalculator {

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다.
    // referenceDate: 현재/기준으로 삼을 날짜.
    // 반환: "현재_TO_다음" 형태의 전환유형 문자열.
    public String calculate(Map<LocalDate, ShiftType> schedules, LocalDate referenceDate) {
        ShiftType current = shiftAt(schedules, referenceDate);
        ShiftType next = shiftAt(schedules, referenceDate.plusDays(1));

        // ShiftType 이름 자체가 DAY/EVENING/NIGHT/OFF 라서
        // 이름을 이어붙이면 자연스럽게 풀네임 전환유형이 된다.
        return current.name() + "_TO_" + next.name();
    }

    // 특정 날짜의 근무유형을 돌려준다. 저장된 값이 없으면 implicit OFF(계산용 OFF)로 본다.
    private ShiftType shiftAt(Map<LocalDate, ShiftType> schedules, LocalDate date) {
        ShiftType shift = schedules.get(date);
        return shift == null ? ShiftType.OFF : shift;
    }
}
