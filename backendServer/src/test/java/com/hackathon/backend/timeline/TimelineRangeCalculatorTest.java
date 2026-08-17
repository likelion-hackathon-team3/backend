package com.hackathon.backend.timeline;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// TimelineRangeCalculator의 4개 그룹 시간 범위 공식을 검증한다.
// ShiftDateTimeResolver를 그대로 재사용하는지(특히 NIGHT roster-date)도 함께 확인한다.
class TimelineRangeCalculatorTest {

    private final TimelineRangeCalculator calculator = new TimelineRangeCalculator();
    private final LocalDate date = LocalDate.of(2026, 8, 17);

    // DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00, commute 30분.
    private Environment standardEnv() {
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                30
        );
    }

    @Test
    void WORK_TO_WORK_공식_DAY_TO_DAY() {
        TimelineRange range = calculator.calculate(date, ShiftType.DAY, ShiftType.DAY, standardEnv());

        assertThat(range.group()).isEqualTo(TimelineGroup.WORK_TO_WORK);
        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 30));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 6, 30));
        assertThat(range.nextWorkActualStart()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));
    }

    @Test
    void WORK_TO_WORK은_Environment_값이_다르면_실제_시간도_다르다() {
        Environment shortCommute = new Environment(
                LocalTime.of(9, 0), LocalTime.of(18, 0),
                LocalTime.of(18, 0), LocalTime.of(2, 0),
                LocalTime.of(2, 0), LocalTime.of(9, 0),
                10
        );

        TimelineRange range = calculator.calculate(date, ShiftType.DAY, ShiftType.DAY, shortCommute);

        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 18, 10));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 8, 50));
    }

    @Test
    void WORK_TO_OFF_DAY_EVENING은_이후_처음_도래하는_07시까지() {
        TimelineRange range = calculator.calculate(date, ShiftType.DAY, ShiftType.OFF, standardEnv());

        assertThat(range.group()).isEqualTo(TimelineGroup.WORK_TO_OFF);
        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 30));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));
        assertThat(range.nextWorkActualStart()).isNull();
    }

    @Test
    void WORK_TO_OFF_NIGHT은_actualEnd_commute_이후_12시간_고정() {
        TimelineRange range = calculator.calculate(date, ShiftType.NIGHT, ShiftType.OFF, standardEnv());

        LocalDateTime expectedStart = LocalDateTime.of(2026, 8, 18, 7, 30);
        assertThat(range.timelineStart()).isEqualTo(expectedStart);
        assertThat(range.timelineEnd()).isEqualTo(expectedStart.plusHours(12));
    }

    @Test
    void OFF_TO_WORK은_07시_고정_시작_다음근무_시작_commute_차감_종료() {
        TimelineRange range = calculator.calculate(date, ShiftType.OFF, ShiftType.DAY, standardEnv());

        assertThat(range.group()).isEqualTo(TimelineGroup.OFF_TO_WORK);
        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 6, 30));
    }

    @Test
    void OFF_TO_WORK_NIGHT_roster_date_규칙이_그대로_반영된다() {
        // NIGHT 01:30~09:30, EVENING 15:00~ : nightStart(01:30) < eveningStart(15:00) -> 다음날 시작.
        Environment env = new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 30), LocalTime.of(9, 30),
                30
        );

        TimelineRange range = calculator.calculate(date, ShiftType.OFF, ShiftType.NIGHT, env);

        // date=8/17(OFF) 기준 다음날(date+1=8/18)에 NIGHT가 배정되어 있고,
        // nightStart(01:30) < eveningStart(15:00)이므로 실제 시작은 roster date + 1일인 8/19 01:30.
        assertThat(range.nextWorkActualStart()).isEqualTo(LocalDateTime.of(2026, 8, 19, 1, 30));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 19, 1, 0));
    }

    @Test
    void OFF_TO_OFF는_07시부터_다음날_07시까지_고정() {
        TimelineRange range = calculator.calculate(date, ShiftType.OFF, ShiftType.OFF, standardEnv());

        assertThat(range.group()).isEqualTo(TimelineGroup.OFF_TO_OFF);
        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));
        assertThat(range.nextWorkActualStart()).isNull();
    }

    // nextMorningAnchor 경계: 06:30 -> 같은 날 07:00 / 07:00 -> 다음날 07:00 / 07:01 -> 다음날 07:00.
    @Test
    void nextMorningAnchor_06시30분_이후는_같은날_07시() {
        // DAY 00:00~06:30 이면 actualEnd+commute(0분)=06:30.
        Environment env = new Environment(
                LocalTime.of(0, 0), LocalTime.of(6, 30),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                0
        );

        TimelineRange range = calculator.calculate(date, ShiftType.DAY, ShiftType.OFF, env);

        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 6, 30));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
    }

    @Test
    void nextMorningAnchor_정각_07시는_다음날_07시() {
        Environment env = new Environment(
                LocalTime.of(0, 0), LocalTime.of(7, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                0
        );

        TimelineRange range = calculator.calculate(date, ShiftType.DAY, ShiftType.OFF, env);

        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));
    }

    @Test
    void nextMorningAnchor_07시01분은_다음날_07시() {
        Environment env = new Environment(
                LocalTime.of(0, 0), LocalTime.of(7, 1),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                0
        );

        TimelineRange range = calculator.calculate(date, ShiftType.DAY, ShiftType.OFF, env);

        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 1));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));
    }

    @Test
    void NIGHT_TO_DAY는_timelineEnd가_timelineStart보다_이르거나_같을_수_있다() {
        TimelineRange range = calculator.calculate(date, ShiftType.NIGHT, ShiftType.DAY, standardEnv());

        // NIGHT actualEnd=8/18 07:00 +30분=07:30, 다음 DAY actualStart=8/18 07:00 -30분=06:30.
        assertThat(range.hasUsableWindow()).isFalse();
        assertThat(range.timelineStart()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 30));
        assertThat(range.timelineEnd()).isEqualTo(LocalDateTime.of(2026, 8, 18, 6, 30));
    }
}
