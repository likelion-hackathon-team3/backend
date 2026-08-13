package com.hackathon.backend.environment.dto;

import com.hackathon.backend.environment.Environment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// GET /api/environment 응답.
// 설정 있음: { "dayShift": {...}, "eveningShift": {...}, "nightShift": {...}, "commuteMinutes": 30 }
// 설정 없음: { "dayShift": null, "eveningShift": null, "nightShift": null, "commuteMinutes": null }
// 명세대로 success wrapper는 넣지 않는다.
@Getter
@AllArgsConstructor
public class EnvironmentResponse {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private ShiftTimeResponse dayShift;
    private ShiftTimeResponse eveningShift;
    private ShiftTimeResponse nightShift;
    private Integer commuteMinutes;

    // 저장된 설정을 명세 JSON 구조로 변환한다(LocalTime -> "HH:mm").
    public static EnvironmentResponse from(Environment env) {
        return new EnvironmentResponse(
                new ShiftTimeResponse(format(env.getDayStart()), format(env.getDayEnd())),
                new ShiftTimeResponse(format(env.getEveningStart()), format(env.getEveningEnd())),
                new ShiftTimeResponse(format(env.getNightStart()), format(env.getNightEnd())),
                env.getCommuteMinutes()
        );
    }

    // 아직 설정하지 않은 경우: 모든 값을 null로 반환한다.
    public static EnvironmentResponse empty() {
        return new EnvironmentResponse(null, null, null, null);
    }

    private static String format(LocalTime time) {
        return time.format(HHMM);
    }
}
