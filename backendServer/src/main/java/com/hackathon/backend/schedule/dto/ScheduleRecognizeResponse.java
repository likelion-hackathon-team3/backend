package com.hackathon.backend.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// POST /api/schedules/recognize 응답.
// 성공/부분성공: { "success": true, "recognizedSchedules": [...], "failedDates": [...] }
// 전체 실패:      { "success": false, "message": "..." }
// @JsonInclude(NON_NULL): 값이 null인 필드는 JSON에서 아예 빼서
//                         성공/실패에 따라 명세와 똑같은 모양을 유지한다(ScheduleSaveResponse와 같은 패턴).
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleRecognizeResponse {
    private boolean success;
    private List<RecognizedScheduleItem> recognizedSchedules; // 성공/부분성공 시에만 채움
    private List<String> failedDates;                          // 성공/부분성공 시에만 채움
    private String message;                                    // 전체 실패 시에만 채움

    public static ScheduleRecognizeResponse success(List<RecognizedScheduleItem> recognizedSchedules,
                                                      List<String> failedDates) {
        return new ScheduleRecognizeResponse(true, recognizedSchedules, failedDates, null);
    }

    public static ScheduleRecognizeResponse failure(String message) {
        return new ScheduleRecognizeResponse(false, null, null, message);
    }
}
