package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;

// 위험도 계산에서 쓰는 "현재 시점 기준 위험 패턴" 값 객체.
// transitionType(TransitionCalculator)과는 별개 개념이다.
// N -> OFF -> D 처럼 직전 실제 근무와 다음 실제 근무 사이의 관계를 표현한다.
//
// previousWorkShift/previousWorkDate가 null이면 "이전 실제 근무 없음"을 뜻한다.
// 이 경우 offDaysBetween은 0으로 저장하지만, 이는 직결 패턴(0일 휴무)을 의미하는 것이 아니다.
// PatternRiskCalculator가 previousWorkShift == null을 가장 먼저 검사해서 무조건 0점 처리한다.
public record WorkTransitionPattern(
        ShiftType previousWorkShift,
        LocalDate previousWorkDate,
        ShiftType nextWorkShift,
        LocalDate nextWorkDate,
        int offDaysBetween
) {
}
