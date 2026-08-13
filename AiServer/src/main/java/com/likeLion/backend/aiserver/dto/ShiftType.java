package com.likeLion.backend.aiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "표준 근무 유형 Enum (DAY, EVENING, NIGHT, OFF, DE, EN, ND, MD 등)")
public enum ShiftType {
    DAY,
    EVENING,
    NIGHT,
    OFF,
    DE,     // Day-Evening 중간/연속 근무
    EN,     // Evening-Night 연속 근무
    ND,     // Night-Day 연속 근무
    MD      // Mid 근무
}
