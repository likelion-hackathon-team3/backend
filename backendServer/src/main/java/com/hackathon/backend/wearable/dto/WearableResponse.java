package com.hackathon.backend.wearable.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// GET /api/wearable-data 응답.
// { "sleepHours": 5.2, "activityLevel": 3400, "heartRate": 76 }
// 응답 필드는 명세 그대로 정확히 3개만 사용한다.
// fatigueLevel / hrv / deviceName / status / userId 등 명세 밖 필드는 절대 추가하지 않는다.
// Timeline 내부 wearableData 구조와는 별개의 응답이므로 이 DTO에 timeline용 필드를 섞지 않는다.
@Getter
@AllArgsConstructor
public class WearableResponse {
    private double sleepHours;   // 시간
    private int activityLevel;   // 걸음 수
    private int heartRate;       // 안정 시 심박수(bpm)
}
