package com.hackathon.backend.analysis.dto;

import com.hackathon.backend.analysis.RecoveryStatus;
import com.hackathon.backend.dailystatus.FatigueLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// CurrentConditionResponse.of()의 표시 문자열 매핑 테스트.
// 순수 변환 로직이라 Spring 컨텍스트 없이 바로 테스트한다.
class CurrentConditionResponseTest {

    // fatigueLevel: 기존 FatigueLevel.getKorean() 매핑을 그대로 재사용하는지 확인.
    @ParameterizedTest(name = "fatigueLevel={0} => {1}")
    @CsvSource({
            "LOW,낮음",
            "MEDIUM,보통",
            "HIGH,높음",
    })
    void fatigueLevel_한글_표시(FatigueLevel fatigueLevel, String expectedKorean) {
        CurrentConditionResponse res = CurrentConditionResponse.of(fatigueLevel, 7.0, RecoveryStatus.GOOD);
        assertThat(res.getFatigueLevel()).isEqualTo(expectedKorean);
    }

    // recoveryStatus: GOOD/RECOVERY_NEEDED/RECOVERY_PRIORITY -> 한글 표시값 매핑 확인.
    @ParameterizedTest(name = "recoveryStatus={0} => {1}")
    @CsvSource({
            "GOOD,양호",
            "RECOVERY_NEEDED,회복 필요",
            "RECOVERY_PRIORITY,회복 우선 필요",
    })
    void recoveryStatus_한글_표시(RecoveryStatus recoveryStatus, String expectedKorean) {
        CurrentConditionResponse res = CurrentConditionResponse.of(FatigueLevel.LOW, 7.0, recoveryStatus);
        assertThat(res.getRecoveryStatus()).isEqualTo(expectedKorean);
    }

    @org.junit.jupiter.api.Test
    void sleepHours는_그대로_전달된다() {
        CurrentConditionResponse res = CurrentConditionResponse.of(FatigueLevel.HIGH, 4.5, RecoveryStatus.RECOVERY_NEEDED);
        assertThat(res.getSleepHours()).isEqualTo(4.5);
    }
}
