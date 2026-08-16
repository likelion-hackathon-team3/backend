package com.hackathon.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

// GET /api/analysis 응답.
// 성공: { "transitionType": ..., "consecutiveDays": ..., "availableHours": ..., "nextShiftMinutes": ...,
//         "riskLevel": ..., "currentCondition": {...} }  (success/message 필드 자체가 없음)
// 실패: { "success": false, "message": "..." }            (성공 전용 필드는 전부 없음)
//
// success를 boolean이 아니라 Boolean(nullable)으로 둔 이유:
// 성공 응답에는 success 필드가 아예 없어야 하므로(명세), null이면 JSON에서 생략되는
// @JsonInclude(NON_NULL) + nullable wrapper 조합을 쓴다(ScheduleSaveResponse와 같은 패턴).
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisResponse {
    private Boolean success;   // 실패 시에만 false로 채움
    private String message;    // 실패 시에만 채움

    private String transitionType;               // 성공 시에만
    private Integer consecutiveDays;              // 성공 시에만
    private Double availableHours;                // 성공 시에만 (표시용 소수 첫째 자리 반올림 값)
    private Long nextShiftMinutes;                // 성공 시에만
    private String riskLevel;                     // 성공 시에만
    private CurrentConditionResponse currentCondition; // 성공 시에만

    // 실패 응답 만들기(근무환경 없음 / 다음 근무 없음 두 확정 케이스에서 사용).
    public static AnalysisResponse fail(String message) {
        return new AnalysisResponse(false, message, null, null, null, null, null, null);
    }

    // 성공 응답 만들기.
    public static AnalysisResponse ok(String transitionType, int consecutiveDays, double availableHours,
                                      long nextShiftMinutes, String riskLevel,
                                      CurrentConditionResponse currentCondition) {
        return new AnalysisResponse(null, null, transitionType, consecutiveDays, availableHours,
                nextShiftMinutes, riskLevel, currentCondition);
    }
}
