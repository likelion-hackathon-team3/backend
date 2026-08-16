package com.hackathon.backend.analysis.dto;

import com.hackathon.backend.analysis.RecoveryStatus;
import com.hackathon.backend.dailystatus.FatigueLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

// GET /api/analysis 응답의 currentCondition 부분.
// { "fatigueLevel": "높음", "sleepHours": 4.5, "recoveryStatus": "회복 필요" }
// activityLevel/heartRate는 recoveryStatus 계산에는 쓰이지만 응답 필드에는 넣지 않는다(명세 그대로).
@Getter
@AllArgsConstructor
public class CurrentConditionResponse {
    private String fatigueLevel;
    private double sleepHours;
    private String recoveryStatus;

    // fatigueLevel은 기존 FatigueLevel.getKorean()을 그대로 재사용한다(매핑 중복 작성 금지).
    // recoveryStatus는 RecoveryStatus에 한글 매핑이 없어 여기서만 변환한다.
    public static CurrentConditionResponse of(FatigueLevel fatigueLevel, double sleepHours, RecoveryStatus recoveryStatus) {
        return new CurrentConditionResponse(fatigueLevel.getKorean(), sleepHours, koreanOf(recoveryStatus));
    }

    private static String koreanOf(RecoveryStatus status) {
        return switch (status) {
            case GOOD -> "양호";
            case RECOVERY_NEEDED -> "회복 필요";
            case RECOVERY_PRIORITY -> "회복 우선 필요";
        };
    }
}
