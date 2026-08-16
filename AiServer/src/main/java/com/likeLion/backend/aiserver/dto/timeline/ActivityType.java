package com.likeLion.backend.aiserver.dto.timeline;

public enum ActivityType {
    SLEEP,      // 메인 수면
    NAP,        // 낮잠 / 쪽잠
    MEAL,       // 식사
    WORK,       // 실제 근무 시간 (DAY, EVENING, NIGHT)
    REST,       // 휴식 / 릴랙스 / 샤워
    EXERCISE,   // 가벼운 운동 / 스트레칭
    FREE        // 자유 시간 / 이동
}
