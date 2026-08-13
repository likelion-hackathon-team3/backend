package com.hackathon.backend.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

// POST /api/environment 응답.
// 성공: { "success": true }
// 실패: { "success": false, "message": "근무 시간을 모두 입력해주세요." }
// @JsonInclude(NON_NULL): 값이 null인 필드는 JSON에서 아예 빼서
//                         성공 시 message가 나오지 않도록 명세와 똑같은 모양을 유지한다.
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnvironmentSaveResponse {
    private boolean success;
    private String message; // 실패 시에만 채움

    // 성공 응답 만들기
    public static EnvironmentSaveResponse ok() {
        return new EnvironmentSaveResponse(true, null);
    }

    // 실패(필수값 누락 등) 응답 만들기 — 명세의 메시지를 그대로 사용한다.
    public static EnvironmentSaveResponse invalid() {
        return new EnvironmentSaveResponse(false, "근무 시간을 모두 입력해주세요.");
    }
}
