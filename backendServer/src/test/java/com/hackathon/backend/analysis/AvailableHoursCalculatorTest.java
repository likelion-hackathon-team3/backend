package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

// AvailableHoursCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
// 가정 Environment: DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00 / 통근 30분.
class AvailableHoursCalculatorTest {

    private final AvailableHoursCalculator calculator = new AvailableHoursCalculator();

    // 테스트용 Environment (public 생성자 재사용)
    private Environment env(int commuteMinutes) {
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),   // DAY
                LocalTime.of(15, 0), LocalTime.of(23, 0),  // EVENING
                LocalTime.of(23, 0), LocalTime.of(7, 0),   // NIGHT (자정 넘김)
                commuteMinutes
        );
    }

    // NIGHT가 00:00에 시작하는 근무환경(EVENING 16:00~00:00, NIGHT 00:00~07:00).
    // nightStart(00:00) < eveningStart(16:00) 이므로 NIGHT 실제 시작은 scheduleDate + 1일.
    private Environment envNightStartsMidnight(int commuteMinutes) {
        return new Environment(
                LocalTime.of(8, 0), LocalTime.of(16, 0),   // DAY
                LocalTime.of(16, 0), LocalTime.of(0, 0),   // EVENING (자정 종료)
                LocalTime.of(0, 0), LocalTime.of(7, 0),    // NIGHT (00:00 시작 → 다음날)
                commuteMinutes
        );
    }

    private Map<LocalDate, ShiftType> schedules(Object... pairs) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(LocalDate.parse((String) pairs[i]), (ShiftType) pairs[i + 1]);
        }
        return map;
    }

    private LocalDate d(String date) {
        return LocalDate.parse(date);
    }

    private LocalDateTime t(String dateTime) {
        return LocalDateTime.parse(dateTime);
    }

    @Test
    void 근무_다음날근무_통근2회() {
        // 8/10 DAY(끝 15:00) -> 8/11 EVENING(시작 15:00) = 24h, 통근 2회(60분) => 23.0
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.EVENING
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-10"), t("2026-08-10T09:00"));
        assertThat(r).hasValue(23.0);
    }

    @Test
    void NIGHT현재_자정넘김_중간OFF_건너뜀() {
        // 8/10 NIGHT(실제 종료 8/11 07:00) -> 8/11 OFF 건너뜀 -> 8/12 DAY(07:00) = 24h, 통근 2회 => 23.0
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.NIGHT,
                "2026-08-11", ShiftType.OFF,
                "2026-08-12", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-10"), t("2026-08-11T00:00"));
        assertThat(r).hasValue(23.0);
    }

    @Test
    void 미등록날짜도_경계에서_건너뜀() {
        // 8/11 미등록도 OFF와 동일하게 건너뛴다. 위와 같은 결과 => 23.0
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.NIGHT,
                "2026-08-12", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-10"), t("2026-08-11T00:00"));
        assertThat(r).hasValue(23.0);
    }

    @Test
    void OFF현재_전날NIGHT가_아직_진행중이면_시작점은_NIGHT종료_통근2회() {
        // 8/13 NIGHT(실제 종료 8/14 07:00), 8/14 OFF, 8/15 DAY(07:00), 현재시각 8/14 05:00
        // NIGHT 종료(07:00) > 현재시각(05:00) => 시작점 07:00, 아직 NIGHT 귀가 남음 => 통근 2회
        // 8/14 07:00 ~ 8/15 07:00 = 24h - 60분 => 23.0
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-14"), t("2026-08-14T05:00"));
        assertThat(r).hasValue(23.0);
    }

    @Test
    void OFF현재_전날NIGHT가_이미_끝났으면_시작점은_현재시각_통근1회() {
        // 위와 동일하나 현재시각 8/14 10:00 => NIGHT 종료(07:00)는 이미 지남 => 시작점 10:00, 통근 1회
        // 8/14 10:00 ~ 8/15 07:00 = 21h - 30분 => 20.5
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-14"), t("2026-08-14T10:00"));
        assertThat(r).hasValue(20.5);
    }

    @Test
    void OFF현재_전날이_NIGHT아니면_시작점은_현재시각_통근1회() {
        // 8/13 DAY(전날 NIGHT 아님), 8/14 미등록(OFF 취급), 8/15 DAY, 현재시각 8/14 09:00
        // 시작점 09:00, 통근 1회 => 8/14 09:00 ~ 8/15 07:00 = 22h - 30분 => 21.5
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.DAY,
                "2026-08-15", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-14"), t("2026-08-14T09:00"));
        assertThat(r).hasValue(21.5);
    }

    @Test
    void 통근시간_차감후_음수면_0으로_처리() {
        // NIGHT(실제 종료 8/11 07:00) -> 8/11 DAY(07:00) = 0h. 통근 2회 차감 => 음수 => 0.0
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.NIGHT,
                "2026-08-11", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-10"), t("2026-08-11T00:00"));
        assertThat(r).hasValue(0.0);
    }

    @Test
    void C_다음근무가_NIGHT00시시작이면_종료점은_scheduleDate_다음날_00시() {
        // env: NIGHT 00:00 시작(=scheduleDate+1일). 8/19 DAY -> 8/20 NIGHT.
        // 현재 DAY 실제 종료 = 8/19 16:00. 다음 근무 NIGHT 실제 시작 = 8/21 00:00 (8/20 아님!).
        // 8/19 16:00 ~ 8/21 00:00 = 32h, 통근 2회(60분) => 31.0
        // (만약 종료점을 8/20 00:00으로 잘못 잡으면 8h-60분=7.0이 되어 구분됨)
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-19", ShiftType.DAY,
                "2026-08-20", ShiftType.NIGHT
        );
        OptionalDouble r = calculator.calculate(
                s, envNightStartsMidnight(30), d("2026-08-19"), t("2026-08-19T09:00"));
        assertThat(r).hasValue(31.0);
    }

    @Test
    void D_OFF현재_전날NIGHT가_00시시작이면_전날종료를_8_20_07시로_인식() {
        // env: NIGHT 00:00 시작. 8/19 NIGHT 실제 = 8/20 00:00 ~ 8/20 07:00.
        // 8/20 OFF(현재), 8/21 DAY. 현재시각 8/20 03:00 => NIGHT 아직 진행 중.
        // 시작점 = 8/20 07:00, 귀가 남음 => 통근 2회. 다음 근무 DAY 시작 = 8/21 08:00.
        // 8/20 07:00 ~ 8/21 08:00 = 25h - 60분 => 24.0
        // (전날 NIGHT 종료를 8/19 07:00으로 잘못 잡으면 시작점이 현재시각 03:00·통근1회가 되어 28.5로 구분됨)
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-19", ShiftType.NIGHT,
                "2026-08-20", ShiftType.OFF,
                "2026-08-21", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(
                s, envNightStartsMidnight(30), d("2026-08-20"), t("2026-08-20T03:00"));
        assertThat(r).hasValue(24.0);
    }

    @Test
    void 미래_실제근무_없으면_empty() {
        // 8/10 DAY 이후 실제 근무가 없다(8/11 OFF뿐) => empty
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.OFF
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-10"), t("2026-08-10T09:00"));
        assertThat(r).isEmpty();
    }
}
