package com.hackathon.backend.analysis;

import com.hackathon.backend.dailystatus.FatigueLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// RecoveryStatusCalculator 단위 테스트.
// 순수 계산 클래스라 Spring 컨텍스트나 DB(H2/MySQL) 없이 바로 테스트한다.
//
// 개별 점수(fatigueScore/sleepScore/activityScore/heartRateScore)는 private이라 직접 검증할 수 없다.
// 대신 나머지 3개 입력을 "baseline"으로 고정해 총점을 GOOD/RECOVERY_NEEDED 경계(3/4점) 바로 앞까지 맞춰두고,
// 검증 대상 입력 하나만 경계값 앞뒤로 바꿔서 최종 RecoveryStatus가 실제로 뒤집히는지로 점수 구현을 검증한다.
class RecoveryStatusCalculatorTest {

    private final RecoveryStatusCalculator calculator = new RecoveryStatusCalculator();

    // ================= activityLevel 경계 =================
    // baseline: fatigueScore(MEDIUM=2) + sleepScore(7.0=0) + heartRateScore(80=1) = 3.
    // activityScore 0점(4999) -> 총점 3 GOOD / 1점(5000) -> 총점 4 RECOVERY_NEEDED.
    @ParameterizedTest(name = "activityLevel={0} => {1}")
    @CsvSource({
            "4999,GOOD",
            "5000,RECOVERY_NEEDED",
    })
    void activityLevel_0점_1점_경계(int activityLevel, RecoveryStatus expected) {
        assertThat(calculator.calculate(FatigueLevel.MEDIUM, 7.0, activityLevel, 80)).isEqualTo(expected);
    }

    // baseline: fatigueScore(MEDIUM=2) + sleepScore(7.0=0) + heartRateScore(70=0) = 2.
    // activityScore 1점(7999) -> 총점 3 GOOD / 2점(8000) -> 총점 4 RECOVERY_NEEDED.
    @ParameterizedTest(name = "activityLevel={0} => {1}")
    @CsvSource({
            "7999,GOOD",
            "8000,RECOVERY_NEEDED",
    })
    void activityLevel_1점_2점_경계(int activityLevel, RecoveryStatus expected) {
        assertThat(calculator.calculate(FatigueLevel.MEDIUM, 7.0, activityLevel, 70)).isEqualTo(expected);
    }

    // ================= heartRate 경계 =================
    // baseline: fatigueScore(MEDIUM=2) + sleepScore(7.0=0) + activityScore(6000=1) = 3.
    // heartRateScore 0점(79) -> 총점 3 GOOD / 1점(80) -> 총점 4 RECOVERY_NEEDED.
    @ParameterizedTest(name = "heartRate={0} => {1}")
    @CsvSource({
            "79,GOOD",
            "80,RECOVERY_NEEDED",
    })
    void heartRate_0점_1점_경계(int heartRate, RecoveryStatus expected) {
        assertThat(calculator.calculate(FatigueLevel.MEDIUM, 7.0, 6000, heartRate)).isEqualTo(expected);
    }

    // baseline: fatigueScore(MEDIUM=2) + sleepScore(7.0=0) + activityScore(4000=0) = 2.
    // heartRateScore 1점(89) -> 총점 3 GOOD / 2점(90) -> 총점 4 RECOVERY_NEEDED.
    @ParameterizedTest(name = "heartRate={0} => {1}")
    @CsvSource({
            "89,GOOD",
            "90,RECOVERY_NEEDED",
    })
    void heartRate_1점_2점_경계(int heartRate, RecoveryStatus expected) {
        assertThat(calculator.calculate(FatigueLevel.MEDIUM, 7.0, 4000, heartRate)).isEqualTo(expected);
    }

    // ================= sleepHours 경계 =================
    // baseline: fatigueScore(MEDIUM=2) + activityScore(4000=0) + heartRateScore(70=0) = 2.
    // sleepScore 0점(7.0) -> 총점 2 GOOD / 2점(6.99) -> 총점 4 RECOVERY_NEEDED.
    @ParameterizedTest(name = "sleepHours={0} => {1}")
    @CsvSource({
            "7.0,GOOD",
            "6.99,RECOVERY_NEEDED",
    })
    void sleepHours_0점_2점_경계(double sleepHours, RecoveryStatus expected) {
        assertThat(calculator.calculate(FatigueLevel.MEDIUM, sleepHours, 4000, 70)).isEqualTo(expected);
    }

    // baseline: fatigueScore(LOW=0) + activityScore(5000=1) + heartRateScore(70=0) = 1.
    // sleepScore 2점(5.0) -> 총점 3 GOOD / 4점(4.99) -> 총점 5 RECOVERY_NEEDED.
    @ParameterizedTest(name = "sleepHours={0} => {1}")
    @CsvSource({
            "5.0,GOOD",
            "4.99,RECOVERY_NEEDED",
    })
    void sleepHours_2점_4점_경계(double sleepHours, RecoveryStatus expected) {
        assertThat(calculator.calculate(FatigueLevel.LOW, sleepHours, 5000, 70)).isEqualTo(expected);
    }

    // ================= fatigueLevel 경계 =================
    // baseline: sleepScore(7.0=0) + activityScore(5000=1) + heartRateScore(80=1) = 2.
    // fatigueScore 0점(LOW) -> 총점 2 GOOD / 2점(MEDIUM) -> 총점 4 RECOVERY_NEEDED.
    @ParameterizedTest(name = "fatigueLevel={0} => {1}")
    @CsvSource({
            "LOW,GOOD",
            "MEDIUM,RECOVERY_NEEDED",
    })
    void fatigueLevel_0점_2점_경계(FatigueLevel fatigueLevel, RecoveryStatus expected) {
        assertThat(calculator.calculate(fatigueLevel, 7.0, 5000, 80)).isEqualTo(expected);
    }

    // baseline: sleepScore(7.0=0) + activityScore(5000=1) + heartRateScore(70=0) = 1.
    // fatigueScore 2점(MEDIUM) -> 총점 3 GOOD / 4점(HIGH) -> 총점 5 RECOVERY_NEEDED.
    @ParameterizedTest(name = "fatigueLevel={0} => {1}")
    @CsvSource({
            "MEDIUM,GOOD",
            "HIGH,RECOVERY_NEEDED",
    })
    void fatigueLevel_2점_4점_경계(FatigueLevel fatigueLevel, RecoveryStatus expected) {
        assertThat(calculator.calculate(fatigueLevel, 7.0, 5000, 70)).isEqualTo(expected);
    }

    // ================= 최종 등급 경계 (총점 3/4/7/8) =================

    // 총점 3 = fatigueScore(2, MEDIUM) + sleepScore(0) + activityScore(1) + heartRateScore(0) -> GOOD.
    @Test
    void 총점_3은_GOOD() {
        assertThat(calculator.calculate(FatigueLevel.MEDIUM, 7.0, 5000, 70)).isEqualTo(RecoveryStatus.GOOD);
    }

    // 총점 4 = fatigueScore(4, HIGH) + sleepScore(0) + activityScore(0) + heartRateScore(0) -> RECOVERY_NEEDED.
    @Test
    void 총점_4는_RECOVERY_NEEDED() {
        assertThat(calculator.calculate(FatigueLevel.HIGH, 7.0, 4000, 70)).isEqualTo(RecoveryStatus.RECOVERY_NEEDED);
    }

    // 총점 7 = fatigueScore(4, HIGH) + sleepScore(2) + activityScore(1) + heartRateScore(0) -> RECOVERY_NEEDED.
    @Test
    void 총점_7은_RECOVERY_NEEDED() {
        assertThat(calculator.calculate(FatigueLevel.HIGH, 6.0, 6000, 70)).isEqualTo(RecoveryStatus.RECOVERY_NEEDED);
    }

    // 총점 8 = fatigueScore(4, HIGH) + sleepScore(2) + activityScore(1) + heartRateScore(1) -> RECOVERY_PRIORITY.
    @Test
    void 총점_8은_RECOVERY_PRIORITY() {
        assertThat(calculator.calculate(FatigueLevel.HIGH, 6.0, 6000, 80)).isEqualTo(RecoveryStatus.RECOVERY_PRIORITY);
    }

    // ================= 실제 조합 예시 =================

    // 피로도 높음(4) + sleepHours 5.3(2) + activityLevel 6420(1) + heartRate 72(0) = 7 -> RECOVERY_NEEDED.
    @Test
    void 예시_A_RECOVERY_NEEDED() {
        assertThat(calculator.calculate(FatigueLevel.HIGH, 5.3, 6420, 72)).isEqualTo(RecoveryStatus.RECOVERY_NEEDED);
    }

    // 피로도 높음(4) + sleepHours 4.5(4) + activityLevel 9000(2) + heartRate 92(2) = 12 -> RECOVERY_PRIORITY.
    @Test
    void 예시_B_RECOVERY_PRIORITY() {
        assertThat(calculator.calculate(FatigueLevel.HIGH, 4.5, 9000, 92)).isEqualTo(RecoveryStatus.RECOVERY_PRIORITY);
    }

    // 피로도 낮음(0) + sleepHours 7.5(0) + activityLevel 4000(0) + heartRate 70(0) = 0 -> GOOD.
    @Test
    void 예시_C_GOOD() {
        assertThat(calculator.calculate(FatigueLevel.LOW, 7.5, 4000, 70)).isEqualTo(RecoveryStatus.GOOD);
    }
}
