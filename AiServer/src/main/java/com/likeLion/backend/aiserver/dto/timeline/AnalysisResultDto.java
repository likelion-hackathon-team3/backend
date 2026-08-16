package com.likeLion.backend.aiserver.dto.timeline;

public record AnalysisResultDto(
        RiskLevel riskLevel,
        RecoveryStatus recoveryStatus,
        FatigueLevel fatigueLevel,
        Double availableHours,
        Integer consecutiveDays
) {
}
