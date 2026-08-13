package com.hackathon.backend.environment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// POST /api/environment 요청 본문.
// {
//   "dayShift":     { "start": "07:00", "end": "15:00" },
//   "eveningShift": { "start": "15:00", "end": "23:00" },
//   "nightShift":   { "start": "23:00", "end": "07:00" },
//   "commuteMinutes": 30
// }
// MVP는 3교대만 지원하므로 세 시간대 + commuteMinutes 모두 필수다.
@Getter
@NoArgsConstructor
public class EnvironmentSaveRequest {
    private ShiftTimeRequest dayShift;
    private ShiftTimeRequest eveningShift;
    private ShiftTimeRequest nightShift;
    private Integer commuteMinutes;
}
