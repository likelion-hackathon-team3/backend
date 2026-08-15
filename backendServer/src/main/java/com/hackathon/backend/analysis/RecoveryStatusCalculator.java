package com.hackathon.backend.analysis;

import com.hackathon.backend.dailystatus.FatigueLevel;

// 현재 피로도 + 웨어러블 목업 데이터(수면/활동/심박)로 recoveryScore를 합산해
// recoveryStatus(GOOD/RECOVERY_NEEDED/RECOVERY_PRIORITY)를 판정하는 순수 계산 클래스.
// DB 조회, 현재시간 조회 등은 하지 않고 입력받은 값만으로 결과를 반환한다.
//
// recoveryScore = fatigueScore + sleepScore + activityScore + heartRateScore
//
// [fatigueScore] (fatigueLevel)
// LOW    -> 0점
// MEDIUM -> 2점
// HIGH   -> 4점
//
// [sleepScore] (sleepHours)
// sleepHours >= 7.0                    -> 0점
// 5.0 <= sleepHours < 7.0              -> 2점
// sleepHours < 5.0                     -> 4점
//
// [activityScore] (activityLevel, 걸음 수)
// activityLevel < 5000                 -> 0점
// 5000 <= activityLevel < 8000         -> 1점
// activityLevel >= 8000                -> 2점
//
// [heartRateScore] (heartRate, 안정시 심박수 bpm)
// heartRate < 80                       -> 0점
// 80 <= heartRate < 90                 -> 1점
// heartRate >= 90                      -> 2점
//
// [최종 판정] recoveryScore 총점
// 0~3   -> GOOD             ("양호")
// 4~7   -> RECOVERY_NEEDED  ("회복 필요")
// 8~12  -> RECOVERY_PRIORITY("회복 우선 필요")
public class RecoveryStatusCalculator {

    public RecoveryStatus calculate(FatigueLevel fatigueLevel, double sleepHours, int activityLevel, int heartRate) {
        int score = fatigueScore(fatigueLevel)
                + sleepScore(sleepHours)
                + activityScore(activityLevel)
                + heartRateScore(heartRate);

        return statusOf(score);
    }

    private int fatigueScore(FatigueLevel fatigueLevel) {
        return switch (fatigueLevel) {
            case LOW -> 0;
            case MEDIUM -> 2;
            case HIGH -> 4;
        };
    }

    private int sleepScore(double sleepHours) {
        if (sleepHours >= 7.0) {
            return 0;
        }
        if (sleepHours >= 5.0) {
            return 2;
        }
        return 4;
    }

    private int activityScore(int activityLevel) {
        if (activityLevel < 5000) {
            return 0;
        }
        if (activityLevel < 8000) {
            return 1;
        }
        return 2;
    }

    private int heartRateScore(int heartRate) {
        if (heartRate < 80) {
            return 0;
        }
        if (heartRate < 90) {
            return 1;
        }
        return 2;
    }

    private RecoveryStatus statusOf(int score) {
        if (score >= 8) {
            return RecoveryStatus.RECOVERY_PRIORITY;
        }
        if (score >= 4) {
            return RecoveryStatus.RECOVERY_NEEDED;
        }
        return RecoveryStatus.GOOD;
    }
}
