package com.hackathon.backend.timeline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

// GET /api/timeline 응답.
// 성공: { "success": true, "isFallback": true, "data": {...} }  (message 없음)
// 실패: { "success": false, "message": "..." }                    (isFallback/data 없음)
//
// isFallback을 Boolean(nullable)으로 두고 실패 응답에서는 null -> JSON에서 생략되게 한다
// (AnalysisResponse와 동일한 nullable-wrapper 패턴). Lombok이 Boolean 필드 isFallback의
// getter를 getIsFallback()으로 만들면 Jackson이 프로퍼티명을 다르게 추론할 수 있어
// @JsonProperty로 "isFallback"을 명시적으로 고정한다.
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineResponse {
    private Boolean success;

    @JsonProperty("isFallback")
    private Boolean isFallback;

    private TimelineData data;
    private String message;

    public static TimelineResponse fail(String message) {
        return new TimelineResponse(false, null, null, message);
    }

    // isFallback 생략 시 기존 fallback 호출부와 동일하게 true로 채운다(의미 변경 없음).
    public static TimelineResponse ok(TimelineData data) {
        return ok(data, true);
    }

    // AI 성공 시 isFallback=false로 명시적으로 만들 때 사용한다.
    public static TimelineResponse ok(TimelineData data, boolean isFallback) {
        return new TimelineResponse(true, isFallback, data, null);
    }
}
