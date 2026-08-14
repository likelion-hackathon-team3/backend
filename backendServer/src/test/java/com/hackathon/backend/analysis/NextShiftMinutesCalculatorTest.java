package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

// NextShiftMinutesCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
class NextShiftMinutesCalculatorTest {

    private final NextShiftMinutesCalculator calculator = new NextShiftMinutesCalculator();

    // 시간대를 자유롭게 지정할 수 있는 Environment 빌더(통근시간은 여기선 무관하므로 0).
    private Environment env(String dayStart, String dayEnd,
                           String eveningStart, String eveningEnd,
                           String nightStart, String nightEnd) {
        return new Environment(
                LocalTime.parse(dayStart), LocalTime.parse(dayEnd),
                LocalTime.parse(eveningStart), LocalTime.parse(eveningEnd),
                LocalTime.parse(nightStart), LocalTime.parse(nightEnd),
                0
        );
    }

    // 자주 쓰는 기본 근무환경: DAY 08~16 / EVENING 16~23 / NIGHT 23~07.
    private Environment baseEnv() {
        return env("08:00", "16:00", "16:00", "23:00", "23:00", "07:00");
    }

    // NIGHT가 00:00에 시작하는 근무환경: EVENING 16~00 / NIGHT 00~07.
    private Environment nightMidnightEnv() {
        return env("08:00", "16:00", "16:00", "00:00", "00:00", "07:00");
    }

    private Map<LocalDate, ShiftType> schedules(Object... pairs) {
        Map<LocalDate, ShiftType> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(LocalDate.parse((String) pairs[i]), (ShiftType) pairs[i + 1]);
        }
        return map;
    }

    private LocalDateTime t(String dateTime) {
        return LocalDateTime.parse(dateTime);
    }

    @Test
    void A_오늘근무가_아직_시작전이면_오늘근무까지() {
        // now 8/20 10:00, 8/20 EVENING(16:00 시작) => 6h = 360
        Map<LocalDate, ShiftType> s = schedules("2026-08-20", ShiftType.EVENING);
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T10:00"))).hasValue(360L);
    }

    @Test
    void B_다음날_DAY() {
        // now 8/20 10:00, 8/21 DAY(08:00) => 22h = 1320
        Map<LocalDate, ShiftType> s = schedules("2026-08-21", ShiftType.DAY);
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T10:00"))).hasValue(1320L);
    }

    @Test
    void C_OFF_건너뛰기() {
        // now 8/20 10:00, 8/21 OFF 건너뜀, 8/22 DAY(08:00) => 46h = 2760
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-21", ShiftType.OFF,
                "2026-08-22", ShiftType.DAY
        );
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T10:00"))).hasValue(2760L);
    }

    @Test
    void D_미등록_날짜_건너뛰기() {
        // now 8/20 10:00, 8/21 미등록 건너뜀, 8/22 DAY(08:00) => 46h = 2760
        Map<LocalDate, ShiftType> s = schedules("2026-08-22", ShiftType.DAY);
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T10:00"))).hasValue(2760L);
    }

    @Test
    void E_NIGHT_23시_시작() {
        // now 8/20 10:00, 8/20 NIGHT(23:00 시작) => 실제 8/20 23:00 => 13h = 780
        Map<LocalDate, ShiftType> s = schedules("2026-08-20", ShiftType.NIGHT);
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T10:00"))).hasValue(780L);
    }

    @Test
    void F_NIGHT_00시_시작() {
        // now 8/20 10:00, 8/20 NIGHT(00:00 시작, evening 16:00) => 실제 8/21 00:00 => 14h = 840
        Map<LocalDate, ShiftType> s = schedules("2026-08-20", ShiftType.NIGHT);
        assertThat(calculator.calculate(s, nightMidnightEnv(), t("2026-08-20T10:00"))).hasValue(840L);
    }

    @Test
    void G_진행중인_전날NIGHT는_제외() {
        // 8/19 NIGHT(00:00 시작) 실제 = 8/20 00:00~07:00, now 8/20 02:00 => 진행 중이라 제외.
        // 다음 실제 근무 = 8/21 DAY(08:00) => 8/20 02:00 ~ 8/21 08:00 = 30h = 1800
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-19", ShiftType.NIGHT,
                "2026-08-21", ShiftType.DAY
        );
        assertThat(calculator.calculate(s, nightMidnightEnv(), t("2026-08-20T02:00"))).hasValue(1800L);
    }

    @Test
    void H_now가_근무시작시각과_같으면_그근무는_제외() {
        // now 8/20 16:00 == 8/20 EVENING 시작(16:00) => 이미 시작으로 보고 제외.
        // 다음 근무 = 8/21 DAY(08:00) => 8/20 16:00 ~ 8/21 08:00 = 16h = 960
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-20", ShiftType.EVENING,
                "2026-08-21", ShiftType.DAY
        );
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T16:00"))).hasValue(960L);
    }

    @Test
    void I_다음_실제근무가_없으면_empty() {
        // now 이후 OFF만 존재 => 실제 근무 후보 없음 => empty
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-20", ShiftType.OFF,
                "2026-08-21", ShiftType.OFF
        );
        assertThat(calculator.calculate(s, baseEnv(), t("2026-08-20T10:00"))).isEmpty();
    }

    @Test
    void J_여러후보중_actualStart가_가장가까운_근무선택() {
        // now 8/20 10:00.
        // 후보: 8/20 NIGHT(00:00 시작) -> 실제 8/21 00:00, 8/22 DAY -> 8/22 08:00. 8/21 OFF는 제외.
        // 단순 날짜순이 아니라 actualStart가 가장 이른 8/21 00:00(=8/20 NIGHT)을 선택 => 14h = 840
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-22", ShiftType.DAY,
                "2026-08-20", ShiftType.NIGHT,
                "2026-08-21", ShiftType.OFF
        );
        assertThat(calculator.calculate(s, nightMidnightEnv(), t("2026-08-20T10:00"))).hasValue(840L);
    }
}
