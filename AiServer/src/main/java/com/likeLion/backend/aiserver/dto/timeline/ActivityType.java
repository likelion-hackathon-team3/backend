package com.likeLion.backend.aiserver.dto.timeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "타임라인 활동 카테고리 Enum")
public enum ActivityType {
    SLEEP,          // 본 수면 (권장 취침)
    NAP,            // 쪽잠 / 낮잠
    PREPARATION,    // 취침 준비 / 조명 낮추기 / 샤워 / 출근 준비
    WAKE_UP,        // 기상 / 햇볕 쬐기 / 물 한잔
    MEAL,           // 식사 (아침, 점심, 저녁, 야식)
    WORK,           // 실제 근무 시작 및 근무 시간
    REST,           // 휴식 / 힐링 / 릴랙스 / 자유시간
    EXERCISE;       // 가벼운 운동 / 스트레칭

    @JsonCreator
    public static ActivityType fromString(String value) {
        if (value == null || value.isBlank()) {
            return REST;
        }
        try {
            return ActivityType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // FREE나 기타 알 수 없는 카테고리가 들어오면 REST로 안전하게 폴백
            return REST;
        }
    }
}
