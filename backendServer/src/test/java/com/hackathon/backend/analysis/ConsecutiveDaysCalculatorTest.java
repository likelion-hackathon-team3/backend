package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// ConsecutiveDaysCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
class ConsecutiveDaysCalculatorTest {

    private final ConsecutiveDaysCalculator calculator = new ConsecutiveDaysCalculator();

    private Map<LocalDate, ShiftType> schedules(Object... pairs) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(LocalDate.parse((String) pairs[i]), (ShiftType) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void DAY_EVENING_NIGHT_연속_3일() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.EVENING,
                "2026-08-12", ShiftType.NIGHT
        );
        int result = calculator.calculate(s, LocalDate.parse("2026-08-12"));
        assertThat(result).isEqualTo(3);
    }

    @Test
    void OFF에서_연속이_끊긴다() {
        // 8/12 OFF 로 끊기므로 8/13 기준 연속근무는 1
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.EVENING,
                "2026-08-12", ShiftType.OFF,
                "2026-08-13", ShiftType.DAY
        );
        int result = calculator.calculate(s, LocalDate.parse("2026-08-13"));
        assertThat(result).isEqualTo(1);
    }

    @Test
    void 미등록_날짜에서_연속이_끊긴다() {
        // 8/12 미등록(implicit OFF) 으로 끊기므로 8/13 기준 연속근무는 1
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.EVENING,
                "2026-08-13", ShiftType.DAY
        );
        int result = calculator.calculate(s, LocalDate.parse("2026-08-13"));
        assertThat(result).isEqualTo(1);
    }

    @Test
    void 기준일이_OFF면_연속근무는_0() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.OFF
        );
        int result = calculator.calculate(s, LocalDate.parse("2026-08-11"));
        assertThat(result).isEqualTo(0);
    }

    @Test
    void 기준일이_미등록이면_연속근무는_0() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY
        );
        int result = calculator.calculate(s, LocalDate.parse("2026-08-11"));
        assertThat(result).isEqualTo(0);
    }
}
