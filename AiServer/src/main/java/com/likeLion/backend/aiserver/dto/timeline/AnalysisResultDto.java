package com.likeLion.backend.aiserver.dto.timeline;

public record AnalysisResultDto(
        RiskLevel riskLevel,
        RecoveryStatus recoveryStatus,
        FatigueLevel fatigueLevel,
        Double availableHours,
        Integer consecutiveDays
) {
    public String riskLevelName() {
        return riskLevel != null ? riskLevel.name() : RiskLevel.NORMAL.name();
    }

    public String recoveryStatusName() {
        return recoveryStatus != null ? recoveryStatus.name() : RecoveryStatus.GOOD.name();
    }

    public String fatigueLevelName() {
        return fatigueLevel != null ? fatigueLevel.name() : FatigueLevel.LOW.name();
    }

    public String formattedAvailableHours() {
        return availableHours != null ? String.format("%.1f", availableHours) : "8.0";
    }

    public String formattedConsecutiveDays() {
        return consecutiveDays != null ? String.valueOf(consecutiveDays) : "0";
    }
}
