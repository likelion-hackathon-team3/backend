package com.hackathon.backend.analysis;

// 회복 상태. RecoveryStatusCalculator가 계산한 recoveryScore 합산 결과를 3단계로 나눈 값.
public enum RecoveryStatus {
    GOOD,
    RECOVERY_NEEDED,
    RECOVERY_PRIORITY
}
