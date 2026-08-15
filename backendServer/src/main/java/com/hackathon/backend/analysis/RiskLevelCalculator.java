package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;

// patternScore + consecutiveScore + availableScore를 합산해 최종 riskLevel을 결정하는 순수 계산 클래스.
// 이미 계산된 값(WorkTransitionPattern, consecutiveDays, availableHours)만 입력으로 받는다.
// ConsecutiveDaysCalculator/AvailableHoursCalculator를 여기서 다시 호출하지 않는다(중복 계산 방지).
//
// [availableScore]
// availableHours >= 11.0        -> 0점
// 8.0 <= availableHours < 11.0  -> 1점
// 6.0 <= availableHours < 8.0   -> 2점
// availableHours < 6.0          -> 3점 (단, 이 경우 아래 강제 규칙으로 바로 DANGER)
//
// [consecutiveScore]
// 0~2일 -> 0점 / 3~4일 -> 1점 / 5일 이상 -> 2점
//
// [기본 판정] totalScore = patternScore + consecutiveScore + availableScore
// 0~2 -> NORMAL / 3~5 -> CAUTION / 6 이상 -> DANGER
//
// [보정] patternScore >= 2인데 기본 판정이 NORMAL이면 최소 CAUTION으로 올린다.
//
// [강제] NIGHT -> DAY 직결(offDaysBetween == 0)이면 무조건 DANGER.
//        availableHours < 6.0이면 무조건 DANGER.
//        강제 규칙은 patternScore == 4 같은 점수가 아니라 실제 패턴 조건을 직접 검사한다
//        (점수표가 나중에 바뀌어도 강제 규칙의 의미가 유지되도록).
public class RiskLevelCalculator {

    private final PatternRiskCalculator patternRiskCalculator = new PatternRiskCalculator();

    public RiskLevel calculate(WorkTransitionPattern pattern, int consecutiveDays, double availableHours) {
        if (isForcedNightToDay(pattern)) {
            return RiskLevel.DANGER;
        }
        if (availableHours < 6.0) {
            return RiskLevel.DANGER;
        }

        int patternScore = patternRiskCalculator.calculate(pattern);
        int totalScore = patternScore + consecutiveScore(consecutiveDays) + availableScore(availableHours);

        RiskLevel base = baseRiskLevel(totalScore);

        if (patternScore >= 2 && base == RiskLevel.NORMAL) {
            return RiskLevel.CAUTION;
        }
        return base;
    }

    private boolean isForcedNightToDay(WorkTransitionPattern pattern) {
        return pattern.previousWorkShift() == ShiftType.NIGHT
                && pattern.nextWorkShift() == ShiftType.DAY
                && pattern.offDaysBetween() == 0;
    }

    private RiskLevel baseRiskLevel(int totalScore) {
        if (totalScore >= 6) {
            return RiskLevel.DANGER;
        }
        if (totalScore >= 3) {
            return RiskLevel.CAUTION;
        }
        return RiskLevel.NORMAL;
    }

    private int consecutiveScore(int consecutiveDays) {
        if (consecutiveDays >= 5) {
            return 2;
        }
        if (consecutiveDays >= 3) {
            return 1;
        }
        return 0;
    }

    private int availableScore(double availableHours) {
        if (availableHours < 6.0) {
            return 3;
        }
        if (availableHours < 8.0) {
            return 2;
        }
        if (availableHours < 11.0) {
            return 1;
        }
        return 0;
    }
}
