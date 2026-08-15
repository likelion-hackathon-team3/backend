package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// TransitionCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
class TransitionCalculatorTest {

    private final TransitionCalculator calculator = new TransitionCalculator();

    // 날짜->근무유형 맵을 편하게 만드는 헬퍼.
    private Map<LocalDate, ShiftType> schedules(Object... pairs) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(LocalDate.parse((String) pairs[i]), (ShiftType) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void DAY_다음날_EVENING() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.EVENING
        );
        String result = calculator.calculate(s, LocalDate.parse("2026-08-10"));
        assertThat(result).isEqualTo("DAY_TO_EVENING");
    }

    @Test
    void EVENING_다음날_DAY() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.EVENING,
                "2026-08-11", ShiftType.DAY
        );
        String result = calculator.calculate(s, LocalDate.parse("2026-08-10"));
        assertThat(result).isEqualTo("EVENING_TO_DAY");
    }

    @Test
    void NIGHT_다음날_OFF() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.NIGHT,
                "2026-08-11", ShiftType.OFF
        );
        String result = calculator.calculate(s, LocalDate.parse("2026-08-10"));
        assertThat(result).isEqualTo("NIGHT_TO_OFF");
    }

    @Test
    void 다음날_Schedule_미등록이면_implicit_OFF로_본다() {
        // 8/11 이 저장돼 있지 않으면 계산상 OFF로 취급 → NIGHT_TO_OFF
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.NIGHT
        );
        String result = calculator.calculate(s, LocalDate.parse("2026-08-10"));
        assertThat(result).isEqualTo("NIGHT_TO_OFF");
    }

    @Test
    void 기준일_미등록이면_현재도_implicit_OFF로_본다() {
        // 8/14 미등록 → OFF, 8/15 DAY → OFF_TO_DAY
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-15", ShiftType.DAY
        );
        String result = calculator.calculate(s, LocalDate.parse("2026-08-14"));
        assertThat(result).isEqualTo("OFF_TO_DAY");
    }
}
