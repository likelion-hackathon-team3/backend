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

    // 이번 3분기(Branch A/B/C) 테스트에서 공통으로 쓰는 환경: DAY 07:30~16:00 / EVENING 16:00~23:00 / NIGHT 23:00~07:30.
    private Environment envUser(int commuteMinutes) {
        return new Environment(
                LocalTime.of(7, 30), LocalTime.of(16, 0),
                LocalTime.of(16, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 30),
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

    // ===== Branch A: referenceDate 근무가 아직 시작 전 =====
    // 이 경우 그 근무 자체가 "다음 근무"가 되어야 한다(미래 근무로 건너뛰면 안 됨).

    @Test
    void BranchA_오늘NIGHT_시작전() {
        // 8/14 NIGHT(23:00~07:30). now 17:00 < actualStart(23:00) => Branch A.
        // (23:00-17:00=360분) - 통근30분 = 330분 = 5.5h
        Map<LocalDate, ShiftType> s = schedules("2026-08-14", ShiftType.NIGHT);
        OptionalDouble r = calculator.calculate(s, envUser(30), d("2026-08-14"), t("2026-08-14T17:00"));
        assertThat(r).hasValue(5.5);
    }

    @Test
    void BranchA_오늘EVENING_시작전() {
        // 8/15 EVENING(16:00~23:00). now 10:00 < actualStart(16:00) => Branch A.
        // (16:00-10:00=360분) - 통근30분 = 330분 = 5.5h
        Map<LocalDate, ShiftType> s = schedules("2026-08-15", ShiftType.EVENING);
        OptionalDouble r = calculator.calculate(s, envUser(30), d("2026-08-15"), t("2026-08-15T10:00"));
        assertThat(r).hasValue(5.5);
    }

    @Test
    void BranchA_NIGHT_rosterDate_시작전() {
        // 8/15 NIGHT, nightStart 01:37 < eveningStart 16:00 => 실제 시작 8/16 01:37 (roster-date 규칙)
        // now 8/15 17:37 < actualStart(8/16 01:37) => Branch A.
        // (8h=480분) - 통근30분 = 450분 = 7.5h
        Environment e = new Environment(
                LocalTime.of(7, 30), LocalTime.of(16, 0),
                LocalTime.of(16, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 37), LocalTime.of(7, 30),
                30
        );
        Map<LocalDate, ShiftType> s = schedules("2026-08-15", ShiftType.NIGHT);
        OptionalDouble r = calculator.calculate(s, e, d("2026-08-15"), t("2026-08-15T17:37"));
        assertThat(r).hasValue(7.5);
    }

    // ===== Branch C: referenceDate 근무가 이미 종료됨 =====

    @Test
    void BranchC_오늘DAY_이미종료() {
        // 8/10 DAY(07:00~15:00), now 20:00 >= actualEnd(15:00) => Branch C.
        // 다음 근무 8/11 EVENING(15:00 시작). (20:00->익일15:00=19h=1140분) - 통근30분(1회) = 1110분 = 18.5h
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-10", ShiftType.DAY,
                "2026-08-11", ShiftType.EVENING
        );
        OptionalDouble r = calculator.calculate(s, env(30), d("2026-08-10"), t("2026-08-10T20:00"));
        assertThat(r).hasValue(18.5);
    }

    // ===== 위험 케이스: 오늘 미시작 근무를 건너뛰고 먼 미래 근무까지 잘못 계산하면 안 됨 =====

    @Test
    void 오늘아직시작안한EVENING을_건너뛰고_먼미래근무까지_계산하면_안됨() {
        // 8/15 EVENING(16:00~23:00, 아직 미시작), 8/17 DAY(07:30 시작)도 등록돼 있음.
        // now 10:00 < actualStart(16:00) => Branch A여야 하며, 8/17 DAY까지 건너뛰면 안 된다.
        // 정답: (16:00-10:00=360분) - 통근30분 = 330분 = 5.5h. (31.5h가 나오면 버그)
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-15", ShiftType.EVENING,
                "2026-08-17", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(s, envUser(30), d("2026-08-15"), t("2026-08-15T10:00"));
        assertThat(r).hasValue(5.5);
    }

    // ================= NIGHT roster-date 버그 회귀 =================
    // DAY(8/14) -> NIGHT(8/15, 01:37 시작) -> OFF(8/16).
    // referenceDate가 8/16(OFF 분기)으로 넘어가도, 아직 시작 안 한 전날(8/15) NIGHT를
    // "다음 근무"로 찾아야 한다(기존 forward-search는 referenceDate+1부터만 훑어서 놓쳤던 부분).

    private Environment envNightStarts0137(int commuteMinutes) {
        return new Environment(
                LocalTime.of(7, 30), LocalTime.of(16, 0),
                LocalTime.of(16, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 37), LocalTime.of(7, 30),
                commuteMinutes
        );
    }

    @Test
    void OFF현재_전날NIGHT가_roster_date_규칙으로_아직_시작_전이면_그_NIGHT까지_통근1회() {
        // NIGHT actualStart = 8/16 01:37. referenceTime = 8/16 00:30(아직 시작 전).
        // (67분 - 통근30분) / 60 = 37/60 h. Branch A와 동일하게 출근 통근 1회만 차감.
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF
        );
        OptionalDouble r = calculator.calculate(
                s, envNightStarts0137(30), d("2026-08-16"), t("2026-08-16T00:30"));
        assertThat(r).hasValue(37.0 / 60.0);
    }

    @Test
    void OFF현재_전날NIGHT_actualStart_정각이면_새분기_미적용_기존로직으로_처리() {
        // referenceTime이 actualStart(01:37)와 정확히 같으면 "이미 시작"으로 보고 새 분기를 타지 않는다.
        // 기존 로직(전날 NIGHT 진행중 체크)이 적용되어 시작점 = NIGHT 실제종료(8/16 07:30), 통근 2회.
        // 8/16 07:30 ~ 8/17 07:30(DAY 시작) = 24h - 60분 => 23.0
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF,
                "2026-08-17", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(
                s, envNightStarts0137(30), d("2026-08-16"), t("2026-08-16T01:37"));
        assertThat(r).hasValue(23.0);
    }

    @Test
    void OFF현재_전날NIGHT_actualStart_직후에도_새분기_미적용() {
        // 진행 중(01:38)에도 새 분기 미적용 -> 정각 테스트와 동일한 결과(23.0).
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF,
                "2026-08-17", ShiftType.DAY
        );
        OptionalDouble r = calculator.calculate(
                s, envNightStarts0137(30), d("2026-08-16"), t("2026-08-16T01:38"));
        assertThat(r).hasValue(23.0);
    }
}
