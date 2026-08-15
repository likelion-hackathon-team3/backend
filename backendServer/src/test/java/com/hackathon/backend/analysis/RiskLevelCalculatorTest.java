package com.hackathon.backend.analysis;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

// RiskLevelCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
// 가정 Environment: DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00 / 통근 30분.
class RiskLevelCalculatorTest {

    private final RiskLevelCalculator calculator = new RiskLevelCalculator();

    private WorkTransitionPattern pattern(ShiftType previous, ShiftType next, int offDaysBetween) {
        LocalDate previousDate = LocalDate.parse("2026-08-01");
        return new WorkTransitionPattern(previous, previousDate, next, previousDate.plusDays(offDaysBetween + 1L), offDaysBetween);
    }

    // ================= 강제 규칙 =================

    // NIGHT -> DAY 직결(offDaysBetween=0)은 점수와 무관하게 무조건 DANGER.
    // consecutiveDays=0, availableHours를 크게 줘서 점수만으로는 CAUTION(4점)에 그친다는 것을 확인한 뒤에도
    // 강제 규칙이 DANGER로 덮어쓰는지 검증한다.
    @Test
    void NIGHT_DAY_직결은_점수와_무관하게_강제_DANGER() {
        WorkTransitionPattern p = pattern(ShiftType.NIGHT, ShiftType.DAY, 0);
        assertThat(calculator.calculate(p, 0, 100.0)).isEqualTo(RiskLevel.DANGER);
    }

    // availableHours < 6.0이면 점수와 무관하게 무조건 DANGER (patternScore=0인 가장 안전한 패턴이어도).
    @Test
    void availableHours_6미만은_점수와_무관하게_강제_DANGER() {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.DAY, 0);
        assertThat(calculator.calculate(p, 0, 5.9)).isEqualTo(RiskLevel.DANGER);
    }

    // ================= 보정 규칙 =================

    // patternScore(2) + consecutiveScore(0) + availableScore(0) = 2 -> 기본 판정은 NORMAL이지만
    // patternScore>=2이므로 최소 CAUTION으로 올라가야 한다.
    @Test
    void patternScore_2이상인데_기본판정이_NORMAL이면_CAUTION으로_보정() {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.NIGHT, 0); // patternScore=2
        assertThat(calculator.calculate(p, 0, 11.0)).isEqualTo(RiskLevel.CAUTION);
    }

    // 대조군: patternScore=1이면 총점이 같은 조건이라도 보정 대상이 아니라 NORMAL 그대로.
    @Test
    void patternScore_1은_보정_대상이_아니다() {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.EVENING, 0); // patternScore=1
        assertThat(calculator.calculate(p, 0, 11.0)).isEqualTo(RiskLevel.NORMAL);
    }

    // ================= availableHours 6/8/11 경계 =================

    // patternScore=0(DAY->DAY), consecutiveDays=3(consecutiveScore=1) 고정 -> 8.0 경계 확인.
    @ParameterizedTest(name = "availableHours={0} => {1}")
    @CsvSource({
            "11.0,NORMAL",
            "10.99,NORMAL",
            "8.0,NORMAL",
            "7.99,CAUTION",
            "6.0,CAUTION",
            "5.99,DANGER",
    })
    void availableHours_8_경계(double availableHours, RiskLevel expected) {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.DAY, 0);
        assertThat(calculator.calculate(p, 3, availableHours)).isEqualTo(expected);
    }

    // patternScore=1(DAY->EVENING), consecutiveDays=3(consecutiveScore=1) 고정 -> 11.0 경계 확인.
    @ParameterizedTest(name = "availableHours={0} => {1}")
    @CsvSource({
            "11.0,NORMAL",
            "10.99,CAUTION",
            "8.0,CAUTION",
            "7.99,CAUTION",
    })
    void availableHours_11_경계(double availableHours, RiskLevel expected) {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.EVENING, 0);
        assertThat(calculator.calculate(p, 3, availableHours)).isEqualTo(expected);
    }

    // ================= consecutiveDays 2/3/4/5 경계 =================

    // patternScore=0(DAY->DAY), availableHours=7.0(availableScore=2) 고정 -> 3일 경계(0점->1점) 확인.
    @ParameterizedTest(name = "consecutiveDays={0} => {1}")
    @CsvSource({
            "2,NORMAL",
            "3,CAUTION",
            "4,CAUTION",
    })
    void consecutiveDays_3_경계(int consecutiveDays, RiskLevel expected) {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.DAY, 0);
        assertThat(calculator.calculate(p, consecutiveDays, 7.0)).isEqualTo(expected);
    }

    // patternScore=2(DAY->NIGHT), availableHours=7.0(availableScore=2) 고정 -> 5일 경계(1점->2점) 확인.
    @ParameterizedTest(name = "consecutiveDays={0} => {1}")
    @CsvSource({
            "2,CAUTION",
            "3,CAUTION",
            "4,CAUTION",
            "5,DANGER",
    })
    void consecutiveDays_5_경계(int consecutiveDays, RiskLevel expected) {
        WorkTransitionPattern p = pattern(ShiftType.DAY, ShiftType.NIGHT, 0);
        assertThat(calculator.calculate(p, consecutiveDays, 7.0)).isEqualTo(expected);
    }

    // ================= 종합 시나리오 (ShiftPatternCalculator + 기존 계산기 + RiskLevelCalculator) =================

    private final ShiftPatternCalculator shiftPatternCalculator = new ShiftPatternCalculator();
    private final ReferenceDateResolver referenceDateResolver = new ReferenceDateResolver();
    private final ConsecutiveDaysCalculator consecutiveDaysCalculator = new ConsecutiveDaysCalculator();
    private final AvailableHoursCalculator availableHoursCalculator = new AvailableHoursCalculator();

    private Environment env() {
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
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

    private RiskLevel riskLevelOf(Map<LocalDate, ShiftType> schedules, LocalDateTime now) {
        Environment environment = env();
        LocalDate referenceDate = referenceDateResolver.resolve(schedules, environment, now);
        WorkTransitionPattern pattern = shiftPatternCalculator.find(schedules, environment, referenceDate, now)
                .orElseThrow(() -> new IllegalStateException("다음 근무 일정이 없습니다."));
        int consecutiveDays = consecutiveDaysCalculator.calculate(schedules, referenceDate);
        OptionalDouble availableHours = availableHoursCalculator.calculate(schedules, environment, referenceDate, now);
        return calculator.calculate(pattern, consecutiveDays, availableHours.getAsDouble());
    }

    // NIGHT -> OFF 1일 -> DAY (patternScore=3, availableHours=23.0h) -> CAUTION.
    @Test
    void 종합_NIGHT_OFF1_DAY_CAUTION() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.DAY
        );
        assertThat(riskLevelOf(s, LocalDateTime.parse("2026-08-13T23:30:00"))).isEqualTo(RiskLevel.CAUTION);
    }

    // NIGHT -> DAY 직결은 강제 규칙과 점수 양쪽 모두로 DANGER.
    @Test
    void 종합_NIGHT_DAY_직결_DANGER() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.DAY
        );
        assertThat(riskLevelOf(s, LocalDateTime.parse("2026-08-13T23:30:00"))).isEqualTo(RiskLevel.DANGER);
    }

    // DAY -> NIGHT 직결(patternScore=2) + availableHours 31.0h(0점) + consecutiveDays 1(0점) = 총점 2(NORMAL)
    // -> patternScore>=2 보정으로 CAUTION.
    @Test
    void 종합_DAY_NIGHT_직결_보정으로_CAUTION() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.DAY,
                "2026-08-14", ShiftType.NIGHT
        );
        assertThat(riskLevelOf(s, LocalDateTime.parse("2026-08-13T10:00:00"))).isEqualTo(RiskLevel.CAUTION);
    }

    // 휴무 3일 이상이면 이전 NIGHT 패턴 영향이 리셋되어 patternScore=0 -> NORMAL.
    @Test
    void 종합_휴무_3일_이상은_패턴_리셋_NORMAL() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-08-14", ShiftType.OFF,
                "2026-08-15", ShiftType.OFF,
                "2026-08-16", ShiftType.OFF,
                "2026-08-17", ShiftType.DAY
        );
        assertThat(riskLevelOf(s, LocalDateTime.parse("2026-08-14T09:00:00"))).isEqualTo(RiskLevel.NORMAL);
    }

    // 8월 NIGHT -> 10월 DAY처럼 먼 미래로 다음 근무를 찾아도 offDaysBetween>=3이라 patternScore=0.
    // consecutiveDays는 지금 NIGHT 근무 중이므로 1(ConsecutiveDaysCalculator 기준)이지만 consecutiveScore는 0.
    @Test
    void 종합_먼_미래_다음근무는_패턴_연결_안함_NORMAL() {
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-13", ShiftType.NIGHT,
                "2026-10-02", ShiftType.DAY
        );
        LocalDateTime now = LocalDateTime.parse("2026-08-13T23:30:00");

        Environment environment = env();
        LocalDate referenceDate = referenceDateResolver.resolve(s, environment, now);
        assertThat(consecutiveDaysCalculator.calculate(s, referenceDate)).isEqualTo(1);

        assertThat(riskLevelOf(s, now)).isEqualTo(RiskLevel.NORMAL);
    }

    // ================= NIGHT roster-date 버그 회귀 (3개 계산기 일치 확인) =================

    // referenceDate가 NIGHT의 roster-date보다 하루 넘어가도(=OFF로 넘어간 상태),
    // NextShiftMinutesCalculator/AvailableHoursCalculator/ShiftPatternCalculator 셋 다
    // 아직 시작 안 한 그 NIGHT를 "다음 근무"로 일치되게 인식해야 한다(모순으로 인한 예외 없음).
    @Test
    void 종합_NIGHT_roster_date_간극에서도_세_계산기가_일치한다() {
        Environment environment = new Environment(
                LocalTime.of(7, 30), LocalTime.of(16, 0),
                LocalTime.of(16, 0), LocalTime.of(23, 0),
                LocalTime.of(1, 37), LocalTime.of(7, 30),
                30
        );
        Map<LocalDate, ShiftType> s = schedules(
                "2026-08-14", ShiftType.DAY,
                "2026-08-15", ShiftType.NIGHT,
                "2026-08-16", ShiftType.OFF
        );
        LocalDateTime now = LocalDateTime.parse("2026-08-16T00:30:00");

        LocalDate referenceDate = referenceDateResolver.resolve(s, environment, now);
        assertThat(referenceDate).isEqualTo(LocalDate.parse("2026-08-16"));

        OptionalLong nextShiftMinutes = new NextShiftMinutesCalculator().calculate(s, environment, now);
        OptionalDouble availableHours = availableHoursCalculator.calculate(s, environment, referenceDate, now);
        Optional<WorkTransitionPattern> pattern = shiftPatternCalculator.find(s, environment, referenceDate, now);

        assertThat(nextShiftMinutes).hasValue(67L);
        assertThat(availableHours).isPresent();
        assertThat(pattern).isPresent();

        assertThat(pattern.get()).isEqualTo(new WorkTransitionPattern(
                ShiftType.DAY, LocalDate.parse("2026-08-14"),
                ShiftType.NIGHT, LocalDate.parse("2026-08-15"),
                0
        ));

        int consecutiveDays = consecutiveDaysCalculator.calculate(s, referenceDate);
        // availableHours가 6시간 미만이라 강제 규칙으로 DANGER (패턴 점수와 무관).
        RiskLevel riskLevel = calculator.calculate(pattern.get(), consecutiveDays, availableHours.getAsDouble());
        assertThat(riskLevel).isEqualTo(RiskLevel.DANGER);
    }
}
