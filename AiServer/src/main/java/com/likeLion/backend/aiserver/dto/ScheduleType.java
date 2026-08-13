package com.likeLion.backend.aiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "근무표 유형 (PERSONAL: 1인/개인 달력, MULTI: 부서 전체 근무표)")
public enum ScheduleType {
    PERSONAL,
    MULTI
}
