package com.hackathon.backend.dailystatus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

// POST /api/daily-status 응답.
// 성공: { "success": true }
// 실패: { "success": false, "message": "올바른 피로도 값을 선택해주세요." }
// @JsonInclude(NON_NULL): 값이 null인 필드는 JSON에서 아예 빼서
//                         성공 시 message가 나오지 않도록 명세와 똑같은 모양을 유지한다.
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyStatusResponse {
    private boolean success;
    private String message; // 실패 시에만 채움

    // 성공 응답 만들기
    public static DailyStatusResponse ok() {
        return new DailyStatusResponse(true, null);
    }

    // 실패(잘못된 피로도 값/누락) 응답 만들기 — 명세의 메시지를 그대로 사용한다.
    public static DailyStatusResponse invalid() {
        return new DailyStatusResponse(false, "올바른 피로도 값을 선택해주세요.");
    }
}
