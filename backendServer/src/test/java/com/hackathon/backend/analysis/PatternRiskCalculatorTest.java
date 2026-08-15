package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// PatternRiskCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
class PatternRiskCalculatorTest {

    private final PatternRiskCalculator calculator = new PatternRiskCalculator();

    // 9x4 점수표 전체(36칸)를 검증한다.
    //                    OFF 0일   OFF 1일   OFF 2일   OFF 3일 이상
    // DAY    -> DAY         0         0         0          0
    // DAY    -> EVENING     1         0         0          0
    // DAY    -> NIGHT       2         1         0          0
    // EVENING-> DAY         3         2         1          0
    // EVENING-> EVENING     0         0         0          0
    // EVENING-> NIGHT       1         1         0          0
    // NIGHT  -> DAY         4         3         1          0
    // NIGHT  -> EVENING     2         2         1          0
    // NIGHT  -> NIGHT       0         0         0          0
    @ParameterizedTest(name = "{0} -> {1}, offDaysBetween={2} => {3}점")
    @CsvSource({
            "DAY,DAY,0,0",
            "DAY,DAY,1,0",
            "DAY,DAY,2,0",
            "DAY,DAY,3,0",
            "DAY,EVENING,0,1",
            "DAY,EVENING,1,0",
            "DAY,EVENING,2,0",
            "DAY,EVENING,3,0",
            "DAY,NIGHT,0,2",
            "DAY,NIGHT,1,1",
            "DAY,NIGHT,2,0",
            "DAY,NIGHT,3,0",
            "EVENING,DAY,0,3",
            "EVENING,DAY,1,2",
            "EVENING,DAY,2,1",
            "EVENING,DAY,3,0",
            "EVENING,EVENING,0,0",
            "EVENING,EVENING,1,0",
            "EVENING,EVENING,2,0",
            "EVENING,EVENING,3,0",
            "EVENING,NIGHT,0,1",
            "EVENING,NIGHT,1,1",
            "EVENING,NIGHT,2,0",
            "EVENING,NIGHT,3,0",
            "NIGHT,DAY,0,4",
            "NIGHT,DAY,1,3",
            "NIGHT,DAY,2,1",
            "NIGHT,DAY,3,0",
            "NIGHT,EVENING,0,2",
            "NIGHT,EVENING,1,2",
            "NIGHT,EVENING,2,1",
            "NIGHT,EVENING,3,0",
            "NIGHT,NIGHT,0,0",
            "NIGHT,NIGHT,1,0",
            "NIGHT,NIGHT,2,0",
            "NIGHT,NIGHT,3,0",
    })
    void 전체_9x4_점수표(ShiftType previous, ShiftType next, int offDaysBetween, int expectedScore) {
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                previous, LocalDate.parse("2026-08-01"),
                next, LocalDate.parse("2026-08-01").plusDays(offDaysBetween + 1),
                offDaysBetween
        );
        assertThat(calculator.calculate(pattern)).isEqualTo(expectedScore);
    }

    // offDaysBetween이 3보다 훨씬 커도(예: 8월 NIGHT -> 10월 DAY) 전부 0점으로 리셋되는지 확인.
    @ParameterizedTest(name = "offDaysBetween={0}이어도 3일 이상과 동일하게 0점")
    @CsvSource({"5", "10", "49"})
    void offDays_3일_이상은_값과_무관하게_전부_리셋(int offDaysBetween) {
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                ShiftType.NIGHT, LocalDate.parse("2026-08-13"),
                ShiftType.DAY, LocalDate.parse("2026-08-13").plusDays(offDaysBetween + 1L),
                offDaysBetween
        );
        assertThat(calculator.calculate(pattern)).isEqualTo(0);
    }

    // previousWorkShift가 없으면 offDaysBetween=0으로 저장돼 있어도
    // 직결 패턴(NIGHT->DAY 0일=4점 등)으로 잘못 해석하지 않고 무조건 0점이어야 한다.
    @Test
    void previousWorkShift가_없으면_offDaysBetween과_무관하게_0점() {
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                null, null,
                ShiftType.DAY, LocalDate.parse("2026-08-14"),
                0
        );
        assertThat(calculator.calculate(pattern)).isEqualTo(0);
    }
}
