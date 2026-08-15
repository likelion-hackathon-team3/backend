package com.hackathon.backend.analysis;

import com.hackathon.backend.schedule.ShiftType;

// WorkTransitionPattern -> patternScore(0~4) 매핑만 담당하는 순수 계산 클래스.
//
// 점수표(확정됨): previousWorkShift -> nextWorkShift 조합(9가지) x offDaysBetween(0/1/2/3일 이상).
// offDaysBetween이 3일 이상이면 실제 값과 무관하게 전부 0점으로 취급한다(이전 패턴 영향 리셋).
//
//                    OFF 0일   OFF 1일   OFF 2일   OFF 3일 이상
// DAY    -> DAY         0         0         0          0
// DAY    -> EVENING     1         0         0          0
// DAY    -> NIGHT       2         1         0          0
// EVENING-> DAY         3         2         1          0
// EVENING-> EVENING     0         0         0          0
// EVENING-> NIGHT       1         1         0          0
// NIGHT  -> DAY         4         3         1          0
// NIGHT  -> EVENING     2         2         1          0
// NIGHT  -> NIGHT       0         0         0          0
public class PatternRiskCalculator {

    // [previousWorkShift][nextWorkShift][offDaysBetween: 0,1,2,3+] 순서의 점수표.
    private static final int[][][] SCORE_TABLE = buildScoreTable();

    public int calculate(WorkTransitionPattern pattern) {
        // 이전 실제 근무가 없으면 offDaysBetween(=0)을 직결 패턴으로 해석하지 않고 무조건 0점.
        if (pattern.previousWorkShift() == null) {
            return 0;
        }

        int offIndex = Math.min(pattern.offDaysBetween(), 3);
        return SCORE_TABLE[index(pattern.previousWorkShift())][index(pattern.nextWorkShift())][offIndex];
    }

    private int index(ShiftType shift) {
        return switch (shift) {
            case DAY -> 0;
            case EVENING -> 1;
            case NIGHT -> 2;
            case OFF -> throw new IllegalArgumentException("OFF는 실제 근무가 아니라 패턴 점수 대상이 아닙니다.");
        };
    }

    private static int[][][] buildScoreTable() {
        int[][][] table = new int[3][3][4];
        table[0][0] = new int[]{0, 0, 0, 0}; // DAY -> DAY
        table[0][1] = new int[]{1, 0, 0, 0}; // DAY -> EVENING
        table[0][2] = new int[]{2, 1, 0, 0}; // DAY -> NIGHT
        table[1][0] = new int[]{3, 2, 1, 0}; // EVENING -> DAY
        table[1][1] = new int[]{0, 0, 0, 0}; // EVENING -> EVENING
        table[1][2] = new int[]{1, 1, 0, 0}; // EVENING -> NIGHT
        table[2][0] = new int[]{4, 3, 1, 0}; // NIGHT -> DAY
        table[2][1] = new int[]{2, 2, 1, 0}; // NIGHT -> EVENING
        table[2][2] = new int[]{0, 0, 0, 0}; // NIGHT -> NIGHT
        return table;
    }
}
