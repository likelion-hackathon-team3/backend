package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// DisplayTransitionCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
//
// risk용 WorkTransitionPattern은 pattern 자체를 직접 만들어 주입해서
// DisplayTransitionCalculator의 분기 로직만 독립적으로 검증한다(A~G).
// 그 다음 ReferenceDateResolver + ShiftPatternCalculator + DisplayTransitionCalculator를
// 실제로 이어붙여 사용자가 확정한 7개 시나리오를 그대로 재현한다(1~7).
class DisplayTransitionCalculatorTest {

    private final DisplayTransitionCalculator calculator = new DisplayTransitionCalculator();

    private Environment env() {
        // DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00 (자정 넘김, 간극 없음)
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                30
        );
    }

    // NIGHT가 01:37에 시작하는 환경(EVENING 16:00~23:00, NIGHT 01:37~07:30).
    // nightStart(01:37) < eveningStart(16:00) 이므로 NIGHT 실제 시작은 scheduleDate + 1일.
    private Environment envNightStarts0137() {
        return new Environment(
                LocalTime.of(7, 30), LocalTime.of(16, 0),
                LocalTime.of(16, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 37), LocalTime.of(7, 30),
                30
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

    // ================= A~G: 분기 로직 자체를 pattern을 직접 주입해서 검증 =================

    // A. referenceDate 근무가 이미 시작(now==actualStart 정각 포함)했으면 pattern 내용과 무관하게
    //    TransitionCalculator(단순 다음날 비교)와 동일한 결과를 낸다.
    @Test
    void A_시작됐으면_pattern_무시하고_단순_다음날_비교() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT
        );
        // pattern은 일부러 완전히 다른(무관한) 값을 넣어서 무시되는지 확인한다.
        WorkTransitionPattern irrelevantPattern = new WorkTransitionPattern(
                ShiftType.EVENING, d("2026-08-01"), ShiftType.EVENING, d("2026-08-02"), 5);

        String result = calculator.calculate(s, env(), d("2026-08-14"), t("2026-08-14T07:00:00"), irrelevantPattern);

        assertThat(result).isEqualTo("DAY_TO_NIGHT"); // 8/14 vs 8/15 단순 비교
    }

    // B. actualStart 1분 전이면 아직 "시작 전" -> pattern 기반 분기를 탄다.
    @Test
    void B_actualStart_1분_전이면_시작_안됨() {
        Map<LocalDate, ShiftType> s = schedules("2026-08-14", ShiftType.DAY);
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                null, null, ShiftType.DAY, d("2026-08-14"), 0);

        String result = calculator.calculate(s, env(), d("2026-08-14"), t("2026-08-14T06:59:00"), pattern);

        assertThat(result).isEqualTo("OFF_TO_DAY");
    }

    // C. 시작 안 됨 + previousWorkShift 있음 + offDaysBetween==0 -> 그 근무를 이월.
    @Test
    void C_휴무없이_이어지면_이전근무를_current로_이월() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT
        );
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                ShiftType.DAY, d("2026-08-14"), ShiftType.NIGHT, d("2026-08-15"), 0);

        String result = calculator.calculate(s, env(), d("2026-08-15"), t("2026-08-15T10:00:00"), pattern);

        assertThat(result).isEqualTo("DAY_TO_NIGHT");
    }

    // D. 시작 안 됨 + offDaysBetween>=1(휴무 있음) -> OFF로 표시(previousWorkShift 이월 안 함).
    @Test
    void D_휴무있으면_OFF로_표시() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-16", ShiftType.EVENING
        );
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                ShiftType.DAY, d("2026-08-13"), ShiftType.EVENING, d("2026-08-16"), 2);

        String result = calculator.calculate(s, env(), d("2026-08-14"), t("2026-08-14T09:00:00"), pattern);

        assertThat(result).isEqualTo("OFF_TO_EVENING");
    }

    // E. 시작 안 됨 + previousWorkShift 자체가 없음 -> OFF로 표시.
    @Test
    void E_이전_실제_근무_없으면_OFF로_표시() {
        Map<LocalDate, ShiftType> s = schedules("2026-08-14", ShiftType.NIGHT);
        WorkTransitionPattern pattern = new WorkTransitionPattern(
                null, null, ShiftType.NIGHT, d("2026-08-14"), 0);

        String result = calculator.calculate(s, env(), d("2026-08-14"), t("2026-08-14T10:00:00"), pattern);

        assertThat(result).isEqualTo("OFF_TO_NIGHT");
    }

    // F. actualStart 정각(now==actualStart)이면 "이미 시작"으로 처리된다(경계값 확정).
    @Test
    void F_actualStart_정각이면_이미_시작한_것으로_처리() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.OFF
        );
        WorkTransitionPattern irrelevantPattern = new WorkTransitionPattern(
                null, null, ShiftType.DAY, d("2026-08-14"), 0);

        String result = calculator.calculate(s, env(), d("2026-08-14"), t("2026-08-14T07:00:00"), irrelevantPattern);

        assertThat(result).isEqualTo("DAY_TO_OFF");
    }

    // G. actualEnd를 지나 같은 날짜 안에서도(이미 종료) "시작됨" 그대로 유지 -> 다음날로의 전환 유지.
    @Test
    void G_근무_종료_후에도_같은_날짜면_전환_유지() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT
        );
        WorkTransitionPattern irrelevantPattern = new WorkTransitionPattern(
                null, null, ShiftType.DAY, d("2026-08-14"), 0);

        // DAY actualEnd(15:00)를 훌쩍 지난 20:00.
        String result = calculator.calculate(s, env(), d("2026-08-14"), t("2026-08-14T20:00:00"), irrelevantPattern);

        assertThat(result).isEqualTo("DAY_TO_NIGHT");
    }

    // ================= 1~7: 확정된 시나리오를 실제 계산기 체인으로 재현 =================
    // ReferenceDateResolver + ShiftPatternCalculator + DisplayTransitionCalculator를 그대로 이어붙인다.

    private final ReferenceDateResolver referenceDateResolver = new ReferenceDateResolver();
    private final ShiftPatternCalculator shiftPatternCalculator = new ShiftPatternCalculator();

    private String displayTransitionOf(Map<LocalDate, ShiftType> schedules, Environment environment, LocalDateTime now) {
        LocalDate referenceDate = referenceDateResolver.resolve(schedules, environment, now);
        WorkTransitionPattern pattern = shiftPatternCalculator.find(schedules, environment, referenceDate, now)
                .orElseThrow(() -> new IllegalStateException("다음 근무 일정이 없습니다."));
        return calculator.calculate(schedules, environment, referenceDate, now, pattern);
    }

    // 8/13 OFF / 8/14 DAY / 8/15 NIGHT 시나리오 (1~5, 7).
    private Map<LocalDate, ShiftType> offDayNightSchedules() {
        return schedules(
                "2026-08-13", ShiftType.OFF,
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT
        );
    }

    // 1. OFF / 오늘 DAY 시작 전 -> OFF_TO_DAY
    @Test
    void 시나리오1_DAY_시작_전_OFF_TO_DAY() {
        String result = displayTransitionOf(offDayNightSchedules(), env(), t("2026-08-14T06:59:00"));
        assertThat(result).isEqualTo("OFF_TO_DAY");
    }

    // 2. DAY / 오늘 NIGHT 시작 전(=DAY 시작 시각) -> DAY_TO_NIGHT
    @Test
    void 시나리오2_DAY_시작_시각_DAY_TO_NIGHT() {
        String result = displayTransitionOf(offDayNightSchedules(), env(), t("2026-08-14T07:00:00"));
        assertThat(result).isEqualTo("DAY_TO_NIGHT");
    }

    // 3. DAY 근무 중 / 내일 NIGHT -> DAY_TO_NIGHT
    @Test
    void 시나리오3_DAY_근무중_DAY_TO_NIGHT() {
        String result = displayTransitionOf(offDayNightSchedules(), env(), t("2026-08-14T10:00:00"));
        assertThat(result).isEqualTo("DAY_TO_NIGHT");
    }

    // 4. DAY 종료 후 같은 날 / 내일 NIGHT -> DAY_TO_NIGHT
    @Test
    void 시나리오4_DAY_종료후_같은날_DAY_TO_NIGHT() {
        String result = displayTransitionOf(offDayNightSchedules(), env(), t("2026-08-14T20:00:00"));
        assertThat(result).isEqualTo("DAY_TO_NIGHT");
    }

    // 5. NIGHT 시작 후 / 다음날 OFF -> NIGHT_TO_OFF
    // (뒤에 8/18 DAY를 하나 더 등록해 ShiftPatternCalculator가 pattern을 찾을 수 있게 한다.
    //  실제 AnalysisService라면 이후 실제 근무가 전혀 없을 때 이 단계 전에 이미
    //  "다음 근무 일정이 없습니다"로 끝나므로, 이 테스트는 그 이후에도 근무가 있는
    //  정상 흐름을 재현한다. started=true 분기는 pattern을 쓰지 않으므로 결과는 동일하다.)
    @Test
    void 시나리오5_NIGHT_시작후_다음날_OFF_NIGHT_TO_OFF() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.OFF,
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF,
                "2026-08-18", ShiftType.DAY
        );
        String result = displayTransitionOf(s, env(), t("2026-08-15T23:30:00"));
        assertThat(result).isEqualTo("NIGHT_TO_OFF");
    }

    // 6. 오늘 OFF / 내일 OFF / 모레 EVENING -> OFF_TO_EVENING
    @Test
    void 시나리오6_연속OFF_건너뛰고_OFF_TO_EVENING() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.OFF,
                "2026-08-16", ShiftType.EVENING
        );
        String result = displayTransitionOf(s, env(), t("2026-08-14T09:00:00"));
        assertThat(result).isEqualTo("OFF_TO_EVENING");
    }

    // 7. NIGHT roster-date 간극: 8/15 NIGHT actualStart=8/16 01:37, now=8/16 00:30
    //    -> referenceDate가 8/16으로 넘어가도 직전 DAY_TO_NIGHT 유지.
    @Test
    void 시나리오7_NIGHT_rosterDate_간극에서도_DAY_TO_NIGHT_유지() {
        String result = displayTransitionOf(offDayNightSchedules(), envNightStarts0137(), t("2026-08-16T00:30:00"));
        assertThat(result).isEqualTo("DAY_TO_NIGHT");
    }

    // 7-1. 같은 시나리오, actualStart 정각(8/16 01:37)에는 이미 시작한 것으로 보고
    //      referenceDate도 전날(8/15)로 되돌아가 NIGHT 기준 단순 다음날 비교로 전환된다.
    // (뒤에 8/18 EVENING을 하나 더 등록해 ShiftPatternCalculator가 pattern을 찾을 수 있게 한다.
    //  started=true 분기는 pattern을 쓰지 않으므로 결과는 동일하다.)
    @Test
    void 시나리오7_1_actualStart_정각부터는_NIGHT_기준으로_전환() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.OFF,
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-18", ShiftType.EVENING
        );
        String result = displayTransitionOf(s, envNightStarts0137(), t("2026-08-16T01:37:00"));
        // 8/15(NIGHT) vs 8/16(미등록 -> implicit OFF)
        assertThat(result).isEqualTo("NIGHT_TO_OFF");
    }
}
