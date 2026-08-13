package com.hackathon.backend.dailystatus;

// 피로도를 나타내는 내부 enum(정해진 값들의 집합).
// 외부 API는 한글("낮음"/"보통"/"높음")을 유지하고,
// 내부(DB/계산)에서는 LOW/MEDIUM/HIGH를 사용해 한글값과 매핑한다.
public enum FatigueLevel {
    LOW("낮음"),
    MEDIUM("보통"),
    HIGH("높음");

    private final String korean;

    FatigueLevel(String korean) {
        this.korean = korean;
    }

    // 추후 분석/응답에서 한글값을 다시 내보낼 때 사용한다.
    public String getKorean() {
        return korean;
    }

    // 한글 API 값을 내부 enum으로 바꾼다. 허용값이 아니거나 null이면 null을 반환한다.
    public static FatigueLevel fromKorean(String value) {
        if (value == null) return null;
        for (FatigueLevel level : values()) {
            if (level.korean.equals(value)) {
                return level;
            }
        }
        return null;
    }
}
