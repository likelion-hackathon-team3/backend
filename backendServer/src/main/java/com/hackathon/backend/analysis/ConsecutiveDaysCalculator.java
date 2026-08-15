package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;

import java.time.LocalDate;
import java.util.Map;

// 연속 근무일수(consecutiveDays)만 계산하는 순수 계산 클래스.
// DB에 직접 접근하지 않고, 이미 조회된 "날짜 -> 근무유형" 데이터만 입력으로 받는다.
//
// 규칙(확정됨):
// - 근무일은 DAY / EVENING / NIGHT 이다.
// - OFF 이거나 해당 날짜의 Schedule 자체가 없으면(implicit OFF) 연속근무가 끊긴 것으로 본다.
// - 기준일(referenceDate)부터 하루씩 과거로 이동하며 근무일이면 세고,
//   OFF/미등록을 만나는 순간 멈춘다.
// - 기준일 자체가 OFF/미등록이면 연속 근무일수는 0이다.
public class ConsecutiveDaysCalculator {

    // schedules: 저장된 근무표(날짜 -> 근무유형). 없는 날짜는 키 자체가 없다.
    // referenceDate: 연속 근무일수를 셀 기준 날짜(이 날짜까지 포함해서 뒤로 센다).
    // 반환: referenceDate까지 이어진 연속 근무일수.
    public int calculate(Map<LocalDate, ShiftType> schedules, LocalDate referenceDate) {
        int count = 0;
        LocalDate date = referenceDate;

        while (true) {
            ShiftType shift = schedules.get(date); // 미등록이면 null

            // 미등록(null) 또는 OFF면 연속근무가 끊긴다 → 여기서 멈춘다.
            if (shift == null || shift == ShiftType.OFF) {
                break;
            }

            // 여기까지 왔으면 DAY/EVENING/NIGHT 중 하나 = 근무일.
            count++;
            date = date.minusDays(1); // 하루 과거로 이동
        }

        return count;
    }
}
