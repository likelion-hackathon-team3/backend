package com.hackathon.backend.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;

// GET /api/dashboard 응답.
// 성공: { "success": true, "alerts": [...] }        (message 없음)
// 실패: { "success": false, "message": "..." }       (alerts 없음)
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {

    private final boolean success;
    private final List<DashboardAlertResponse> alerts; // 성공 시에만 채움
    private final String message;                      // 실패 시에만 채움

    private DashboardResponse(boolean success, List<DashboardAlertResponse> alerts, String message) {
        this.success = success;
        this.alerts = alerts;
        this.message = message;
    }

    public static DashboardResponse ok(List<DashboardAlertResponse> alerts) {
        return new DashboardResponse(true, alerts, null);
    }

    public static DashboardResponse fail(String message) {
        return new DashboardResponse(false, null, message);
    }
}
