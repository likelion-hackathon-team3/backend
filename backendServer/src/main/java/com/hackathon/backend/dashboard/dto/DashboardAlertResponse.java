package com.hackathon.backend.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;

// GET /api/dashboard 응답의 alerts 배열 원소 하나.
// { "date", "transitionType", "riskLevel", "title", "message", "recommendation", "isRead" }
@Getter
public class DashboardAlertResponse {

    private final String date;
    private final String transitionType;
    private final String riskLevel;
    private final String title;
    private final String message;
    private final String recommendation;

    // Lombok이 boolean 필드에 만드는 getter는 항상 "isXxx" 형태라서, Jackson이 "is"를 다시 벗겨내
    // JSON 키가 "read"가 돼버린다(자바빈 표준 규칙). 명세의 "isRead" 키를 그대로 유지하기 위해
    // 이 필드만 Lombok 생성을 끄고 아래에 @JsonProperty를 붙인 getter를 직접 둔다.
    @Getter(AccessLevel.NONE)
    private final boolean isRead;

    public DashboardAlertResponse(String date, String transitionType, String riskLevel,
                                   String title, String message, String recommendation) {
        this.date = date;
        this.transitionType = transitionType;
        this.riskLevel = riskLevel;
        this.title = title;
        this.message = message;
        this.recommendation = recommendation;
        this.isRead = false; // MVP: 항상 false. 읽음 상태 저장은 이번 범위 아님.
    }

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }
}
