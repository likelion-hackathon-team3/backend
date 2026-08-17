package com.hackathon.backend.timeline;

// Timeline 내부 전용 활동 세분화. API에는 노출하지 않는다(내부 title/description 문구 선택용).
// 외부 category로는 category()가 반환하는 9개 값(ActivityCategory)으로만 매핑한다.
// 예: SLEEP_PREP -> PREPARATION, RECOVERY_SLEEP -> SLEEP, PRE_WORK_MEAL -> MEAL.
enum ActivityKind {
    POST_WORK_MEAL(ActivityCategory.MEAL),
    POST_WORK_LIGHT_MEAL(ActivityCategory.MEAL),
    POST_WORK_REST(ActivityCategory.REST),

    DAY_WAKE_UP(ActivityCategory.WAKE_UP),
    BREAKFAST(ActivityCategory.MEAL),
    LUNCH(ActivityCategory.MEAL),
    DINNER(ActivityCategory.MEAL),
    DAYTIME_EXERCISE(ActivityCategory.EXERCISE),
    DAYTIME_REST(ActivityCategory.REST),
    DAYTIME_NAP(ActivityCategory.NAP),

    SLEEP_PREP(ActivityCategory.PREPARATION),
    NORMAL_SLEEP(ActivityCategory.SLEEP),
    RECOVERY_SLEEP(ActivityCategory.SLEEP),

    POST_SLEEP_WAKE_UP(ActivityCategory.WAKE_UP),
    POST_SLEEP_MEAL(ActivityCategory.MEAL),

    PRE_NIGHT_NAP(ActivityCategory.NAP),
    POST_NAP_WAKE_UP(ActivityCategory.WAKE_UP),

    PRE_WORK_MEAL(ActivityCategory.MEAL),
    PRE_NIGHT_MEAL(ActivityCategory.MEAL),
    PRE_WORK_PREPARATION(ActivityCategory.PREPARATION),

    WORK_START(ActivityCategory.WORK);

    private final ActivityCategory category;

    ActivityKind(ActivityCategory category) {
        this.category = category;
    }

    ActivityCategory category() {
        return category;
    }
}
