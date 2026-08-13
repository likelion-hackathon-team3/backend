package com.hackathon.backend.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// POST /api/schedules 응답.
// 성공: { "success": true, "savedCount": 2 }
// 실패: { "success": false, "message": "...", "invalidDates": [...] }
// @JsonInclude(NON_NULL): 값이 null인 필드는 JSON에서 아예 빼서
//                         성공/실패에 따라 명세와 똑같은 모양을 유지한다.
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleSaveResponse {
    private boolean success;
    private Integer savedCount;      // 성공 시에만 채움
    private String message;          // 실패 시에만 채움
    private List<String> invalidDates; // 실패 시에만 채움

    // 성공 응답 만들기
    public static ScheduleSaveResponse ok(int savedCount) {
        return new ScheduleSaveResponse(true, savedCount, null, null);
    }

    // 실패(잘못된 근무 유형 포함) 응답 만들기
    public static ScheduleSaveResponse invalid(List<String> invalidDates) {
        return new ScheduleSaveResponse(false, null,
                "잘못된 근무 유형이 포함되어 있습니다.", invalidDates);
    }
}
