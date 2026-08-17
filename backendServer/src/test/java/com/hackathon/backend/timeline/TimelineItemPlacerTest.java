package com.hackathon.backend.timeline;

import com.hackathon.backend.environment.Environment;
import com.hackathon.backend.schedule.ShiftType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// TimelineItemPlacer(fallback Timeline 배치기) 검증.
// 16개 전환 전부를 최소 1회씩 예외 없이 처리하는지 확인하고,
// 대표 전환은 실제 LocalDateTime까지 상세 검증한다.
class TimelineItemPlacerTest {

    private final TimelineRangeCalculator rangeCalculator = new TimelineRangeCalculator();
    private final TimelineBudgetClassifier budgetClassifier = new TimelineBudgetClassifier();
    private final TimelineItemPlacer placer = new TimelineItemPlacer();

    private final LocalDate date = LocalDate.of(2026, 8, 17);

    // DAY 07:00~15:00 / EVENING 15:00~23:00 / NIGHT 23:00~07:00, commute 30분.
    private Environment standardEnv() {
        return new Environment(
                LocalTime.of(7, 0), LocalTime.of(15, 0),
                LocalTime.of(15, 0), LocalTime.of(23, 0),
                LocalTime.of(23, 0), LocalTime.of(7, 0),
                30
        );
    }

    private List<TimelineItemDraft> place(ShiftType current, ShiftType next) {
        TimelineRange range = rangeCalculator.calculate(date, current, next, standardEnv());
        TimelineBudgetLevel budget = budgetClassifier.classify(range.timelineStart(), range.timelineEnd());
        return placer.place(current, next, range, budget);
    }

    private static Stream<Arguments> all16Transitions() {
        ShiftType[] shifts = {ShiftType.DAY, ShiftType.EVENING, ShiftType.NIGHT, ShiftType.OFF};
        return Stream.of(shifts).flatMap(c -> Stream.of(shifts).map(n -> Arguments.of(c, n)));
    }

    @ParameterizedTest
    @MethodSource("all16Transitions")
    void 모든_16개_전환이_예외_없이_deterministic하게_처리된다(ShiftType current, ShiftType next) {
        List<TimelineItemDraft> items = place(current, next);

        assertThat(items).isNotNull();
        assertInvariants(items, current, next);
    }

    // 공통 invariant: WORK 마커(start==end) 제외 모든 아이템이 시간순 정렬 + 겹침 없음 + 범위 안,
    // WORK 마커가 있으면 nextWorkActualStart와 정확히 일치, 없어야 할 그룹에는 없음.
    private void assertInvariants(List<TimelineItemDraft> items, ShiftType current, ShiftType next) {
        TimelineRange range = rangeCalculator.calculate(date, current, next, standardEnv());

        List<TimelineItemDraft> durationItems = items.stream()
                .filter(i -> !i.start().equals(i.end()))
                .sorted(Comparator.comparing(TimelineItemDraft::start))
                .toList();

        // 원래 리스트 순서가 이미 시간순인지(정렬 결과와 동일한지).
        List<TimelineItemDraft> nonWorkInOriginalOrder = items.stream()
                .filter(i -> !i.start().equals(i.end()))
                .toList();
        assertThat(nonWorkInOriginalOrder).isEqualTo(durationItems);

        for (TimelineItemDraft item : durationItems) {
            assertThat(item.end()).isAfter(item.start());
            assertThat(item.start()).isAfterOrEqualTo(range.timelineStart());
            assertThat(item.end()).isBeforeOrEqualTo(range.timelineEnd());
        }

        for (int i = 1; i < durationItems.size(); i++) {
            assertThat(durationItems.get(i).start())
                    .as("겹치면 안 됨: %s vs %s", durationItems.get(i - 1), durationItems.get(i))
                    .isAfterOrEqualTo(durationItems.get(i - 1).end());
        }

        List<TimelineItemDraft> markers = items.stream().filter(i -> i.start().equals(i.end())).toList();
        if (range.nextWorkActualStart() != null) {
            assertThat(markers).hasSize(1);
            assertThat(markers.get(0).kind()).isEqualTo(ActivityKind.WORK_START);
            assertThat(markers.get(0).start()).isEqualTo(range.nextWorkActualStart());
        } else {
            assertThat(markers).isEmpty();
        }
    }

    private void assertSleepImmediatelyFollowedByWakeUp(List<TimelineItemDraft> items,
                                                         ActivityKind sleepKind, ActivityKind wakeKind) {
        TimelineItemDraft sleep = itemOf(items, sleepKind);
        TimelineItemDraft wake = itemOf(items, wakeKind);
        assertThat(sleep.end()).isEqualTo(wake.start());
    }

    private TimelineItemDraft itemOf(List<TimelineItemDraft> items, ActivityKind kind) {
        return items.stream().filter(i -> i.kind() == kind).findFirst()
                .orElseThrow(() -> new AssertionError("기대한 kind가 없음: " + kind));
    }

    // OFF 첫날 템플릿과 NIGHT 당일 낮 anchor가 같은 kind(LUNCH/DAYTIME_REST/DINNER)를
    // 날짜만 다르게 두 번 쓰는 경우가 있어 findFirst만으로는 구분할 수 없다.
    private List<TimelineItemDraft> itemsOf(List<TimelineItemDraft> items, ActivityKind kind) {
        return items.stream().filter(i -> i.kind() == kind).toList();
    }

    // ================= 대표 케이스 상세 검증 =================

    @Test
    void DAY_TO_DAY_상세() {
        List<TimelineItemDraft> items = place(ShiftType.DAY, ShiftType.DAY);

        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.POST_WORK_MEAL, ActivityKind.SLEEP_PREP, ActivityKind.NORMAL_SLEEP,
                ActivityKind.POST_SLEEP_WAKE_UP, ActivityKind.PRE_WORK_MEAL,
                ActivityKind.PRE_WORK_PREPARATION, ActivityKind.WORK_START
        );
        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        TimelineItemDraft work = itemOf(items, ActivityKind.WORK_START);
        assertThat(work.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));

        assertInvariants(items, ShiftType.DAY, ShiftType.DAY);
    }

    @Test
    void DAY_TO_EVENING_상세() {
        List<TimelineItemDraft> items = place(ShiftType.DAY, ShiftType.EVENING);

        TimelineItemDraft sleepPrep = itemOf(items, ActivityKind.SLEEP_PREP);
        assertThat(sleepPrep.start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 22, 30));

        TimelineItemDraft sleep = itemOf(items, ActivityKind.NORMAL_SLEEP);
        assertThat(Duration.between(sleep.start(), sleep.end()).toMinutes()).isEqualTo(540); // 9시간 상한

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        TimelineItemDraft preWorkMeal = itemOf(items, ActivityKind.PRE_WORK_MEAL);
        TimelineItemDraft prep = itemOf(items, ActivityKind.PRE_WORK_PREPARATION);
        TimelineItemDraft work = itemOf(items, ActivityKind.WORK_START);
        assertThat(preWorkMeal.end()).isEqualTo(prep.start());
        assertThat(prep.end()).isEqualTo(work.start().minusMinutes(30));
        assertThat(work.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 0));

        assertInvariants(items, ShiftType.DAY, ShiftType.EVENING);
    }

    @Test
    void EVENING_TO_DAY_상세() {
        List<TimelineItemDraft> items = place(ShiftType.EVENING, ShiftType.DAY);

        assertThat(items).extracting(TimelineItemDraft::kind).doesNotContain(
                ActivityKind.POST_WORK_REST, ActivityKind.DAYTIME_EXERCISE
        );

        TimelineItemDraft sleepPrep = itemOf(items, ActivityKind.SLEEP_PREP);
        // 22:30 anchor가 이미 지나 있어 즉시(퇴근+식사 직후) 취침 준비로 넘어간다.
        assertThat(sleepPrep.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 0, 0));

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        TimelineItemDraft work = itemOf(items, ActivityKind.WORK_START);
        assertThat(work.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));

        assertInvariants(items, ShiftType.EVENING, ShiftType.DAY);
    }

    @Test
    void DAY_TO_NIGHT_상세() {
        List<TimelineItemDraft> items = place(ShiftType.DAY, ShiftType.NIGHT);

        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.POST_WORK_REST, ActivityKind.POST_WORK_MEAL,
                ActivityKind.SLEEP_PREP, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP,
                ActivityKind.POST_SLEEP_MEAL, ActivityKind.LUNCH, ActivityKind.DAYTIME_REST, ActivityKind.DINNER,
                ActivityKind.PRE_NIGHT_NAP, ActivityKind.POST_NAP_WAKE_UP,
                ActivityKind.PRE_NIGHT_MEAL, ActivityKind.PRE_WORK_PREPARATION, ActivityKind.WORK_START
        );

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);
        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.PRE_NIGHT_NAP, ActivityKind.POST_NAP_WAKE_UP);

        // 퇴근 직후 바로 식사시키지 않고 REST -> 저녁(18:00) MEAL 순서로 배치되는지 확인.
        TimelineItemDraft rest = itemOf(items, ActivityKind.POST_WORK_REST);
        TimelineItemDraft meal = itemOf(items, ActivityKind.POST_WORK_MEAL);
        assertThat(rest.start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 30));
        assertThat(rest.end()).isBeforeOrEqualTo(meal.start());
        assertThat(meal.start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 18, 0));

        // normal sleep 이후 낮잠 전까지 LUNCH/REST/DINNER anchor로 공백을 채우는지 확인.
        assertThat(itemOf(items, ActivityKind.LUNCH).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 30));
        assertThat(itemOf(items, ActivityKind.DAYTIME_REST).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 0));
        assertThat(itemOf(items, ActivityKind.DINNER).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 18, 30));

        TimelineItemDraft nap = itemOf(items, ActivityKind.PRE_NIGHT_NAP);
        assertThat(nap.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 20, 0));
        assertThat(nap.end()).isEqualTo(LocalDateTime.of(2026, 8, 18, 21, 30));

        TimelineItemDraft sleep = itemOf(items, ActivityKind.NORMAL_SLEEP);
        assertThat(sleep.end()).isBeforeOrEqualTo(nap.start()); // 겹치지 않음

        TimelineItemDraft work = itemOf(items, ActivityKind.WORK_START);
        assertThat(work.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 23, 0));

        assertInvariants(items, ShiftType.DAY, ShiftType.NIGHT);
    }

    @Test
    void EVENING_TO_NIGHT_상세() {
        List<TimelineItemDraft> items = place(ShiftType.EVENING, ShiftType.NIGHT);

        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.POST_WORK_LIGHT_MEAL, ActivityKind.SLEEP_PREP, ActivityKind.NORMAL_SLEEP,
                ActivityKind.POST_SLEEP_WAKE_UP, ActivityKind.PRE_NIGHT_NAP, ActivityKind.POST_NAP_WAKE_UP,
                ActivityKind.PRE_NIGHT_MEAL, ActivityKind.PRE_WORK_PREPARATION, ActivityKind.WORK_START
        );

        TimelineItemDraft sleepPrep = itemOf(items, ActivityKind.SLEEP_PREP);
        // 퇴근이 이미 늦어 22:30 anchor를 기다리지 않고 즉시 취침 준비로 넘어간다.
        assertThat(sleepPrep.start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 23, 45));

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);
        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.PRE_NIGHT_NAP, ActivityKind.POST_NAP_WAKE_UP);

        assertInvariants(items, ShiftType.EVENING, ShiftType.NIGHT);
    }

    @Test
    void NIGHT_TO_NIGHT_상세() {
        List<TimelineItemDraft> items = place(ShiftType.NIGHT, ShiftType.NIGHT);

        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.POST_WORK_LIGHT_MEAL, ActivityKind.SLEEP_PREP, ActivityKind.RECOVERY_SLEEP,
                ActivityKind.POST_SLEEP_WAKE_UP, ActivityKind.POST_SLEEP_MEAL,
                ActivityKind.PRE_NIGHT_MEAL, ActivityKind.PRE_WORK_PREPARATION, ActivityKind.WORK_START
        );

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.RECOVERY_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        TimelineItemDraft wake = itemOf(items, ActivityKind.POST_SLEEP_WAKE_UP);
        // 일반 DAY처럼 이른 기상이 아니라 늦은 오후 기상이어야 한다.
        assertThat(wake.start().toLocalTime()).isAfterOrEqualTo(LocalTime.of(12, 0));

        long mealCount = items.stream().filter(i -> i.category() == ActivityCategory.MEAL).count();
        assertThat(mealCount).isEqualTo(3); // 퇴근 직후 + 기상 직후 + 다음 NIGHT 전, 별도 3끼

        assertInvariants(items, ShiftType.NIGHT, ShiftType.NIGHT);
    }

    @Test
    void NIGHT_TO_EVENING_상세() {
        List<TimelineItemDraft> items = place(ShiftType.NIGHT, ShiftType.EVENING);

        assertThat(items).extracting(TimelineItemDraft::kind).doesNotContain(
                ActivityKind.DAYTIME_EXERCISE, ActivityKind.PRE_NIGHT_NAP, ActivityKind.DAYTIME_NAP
        );
        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.POST_WORK_LIGHT_MEAL, ActivityKind.SLEEP_PREP, ActivityKind.RECOVERY_SLEEP,
                ActivityKind.POST_SLEEP_WAKE_UP, ActivityKind.PRE_WORK_MEAL,
                ActivityKind.PRE_WORK_PREPARATION, ActivityKind.WORK_START
        );

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.RECOVERY_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        assertInvariants(items, ShiftType.NIGHT, ShiftType.EVENING);
    }

    @Test
    void NIGHT_TO_OFF_상세() {
        List<TimelineItemDraft> items = place(ShiftType.NIGHT, ShiftType.OFF);

        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.SLEEP_PREP, ActivityKind.RECOVERY_SLEEP,
                ActivityKind.POST_SLEEP_WAKE_UP, ActivityKind.POST_SLEEP_MEAL, ActivityKind.DAYTIME_REST
        );
        assertThat(items).extracting(TimelineItemDraft::kind)
                .doesNotContain(ActivityKind.WORK_START, ActivityKind.DAYTIME_EXERCISE);

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.RECOVERY_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        // 회복 수면이 남는 시간을 전부 채우지 않고 최대 8시간으로 제한되는지 확인.
        TimelineItemDraft sleep = itemOf(items, ActivityKind.RECOVERY_SLEEP);
        assertThat(Duration.between(sleep.start(), sleep.end()).toMinutes()).isEqualTo(480);
        assertThat(sleep.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 50));
        assertThat(sleep.end()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 50));

        // 기상 직후 REST, 그 뒤 저녁 시간대 MEAL 순서로 배치되는지 확인.
        TimelineItemDraft wake = itemOf(items, ActivityKind.POST_SLEEP_WAKE_UP);
        TimelineItemDraft rest = itemOf(items, ActivityKind.DAYTIME_REST);
        TimelineItemDraft meal = itemOf(items, ActivityKind.POST_SLEEP_MEAL);
        assertThat(wake.end()).isEqualTo(rest.start());
        assertThat(rest.end()).isBeforeOrEqualTo(meal.start());
        assertThat(meal.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 18, 0));

        assertInvariants(items, ShiftType.NIGHT, ShiftType.OFF);
    }

    @Test
    void OFF_TO_DAY_상세() {
        List<TimelineItemDraft> items = place(ShiftType.OFF, ShiftType.DAY);

        assertThat(itemOf(items, ActivityKind.DAY_WAKE_UP).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
        assertThat(itemOf(items, ActivityKind.BREAKFAST).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 30));
        assertThat(itemOf(items, ActivityKind.DAYTIME_EXERCISE).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 10, 0));
        assertThat(itemOf(items, ActivityKind.LUNCH).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 12, 30));
        assertThat(itemOf(items, ActivityKind.DAYTIME_REST).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 0));
        assertThat(itemOf(items, ActivityKind.DINNER).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 18, 30));
        assertThat(itemOf(items, ActivityKind.SLEEP_PREP).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 22, 30));

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        assertInvariants(items, ShiftType.OFF, ShiftType.DAY);
    }

    @Test
    void OFF_TO_EVENING_상세() {
        List<TimelineItemDraft> items = place(ShiftType.OFF, ShiftType.EVENING);

        TimelineItemDraft sleep = itemOf(items, ActivityKind.NORMAL_SLEEP);
        assertThat(Duration.between(sleep.start(), sleep.end()).toMinutes()).isEqualTo(540);

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);

        TimelineItemDraft work = itemOf(items, ActivityKind.WORK_START);
        assertThat(work.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 0));

        assertInvariants(items, ShiftType.OFF, ShiftType.EVENING);
    }

    @Test
    void OFF_TO_NIGHT_상세_24시간_초과() {
        List<TimelineItemDraft> items = place(ShiftType.OFF, ShiftType.NIGHT);

        assertThat(itemOf(items, ActivityKind.DAY_WAKE_UP).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));

        TimelineItemDraft sleep = itemOf(items, ActivityKind.NORMAL_SLEEP);
        assertThat(sleep.start().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(sleep.end().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 18));

        TimelineItemDraft nap = itemOf(items, ActivityKind.PRE_NIGHT_NAP);
        assertThat(nap.start().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(sleep.end()).isBeforeOrEqualTo(nap.start());

        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.NORMAL_SLEEP, ActivityKind.POST_SLEEP_WAKE_UP);
        assertSleepImmediatelyFollowedByWakeUp(items, ActivityKind.PRE_NIGHT_NAP, ActivityKind.POST_NAP_WAKE_UP);

        // OFF 첫날 기존 fixed anchor 템플릿은 그대로 유지되는지 확인(같은 kind가 NIGHT 당일에도
        // 다시 나오므로 itemsOf로 날짜별 두 항목을 구분해서 검증한다).
        List<TimelineItemDraft> lunches = itemsOf(items, ActivityKind.LUNCH);
        List<TimelineItemDraft> rests = itemsOf(items, ActivityKind.DAYTIME_REST);
        List<TimelineItemDraft> dinners = itemsOf(items, ActivityKind.DINNER);
        assertThat(lunches).hasSize(2);
        assertThat(rests).hasSize(2);
        assertThat(dinners).hasSize(2);

        assertThat(lunches.get(0).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 12, 30));
        assertThat(rests.get(0).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 0));
        assertThat(dinners.get(0).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 18, 30));

        // normal sleep 이후 NIGHT 당일에도 LUNCH/REST/DINNER anchor로 낮잠 전까지의 공백을 채우는지 확인.
        assertThat(lunches.get(1).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 12, 30));
        assertThat(rests.get(1).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 15, 0));
        assertThat(dinners.get(1).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 18, 30));
        assertThat(sleep.end()).isBeforeOrEqualTo(lunches.get(1).start());
        assertThat(dinners.get(1).end()).isBeforeOrEqualTo(nap.start());

        TimelineItemDraft work = itemOf(items, ActivityKind.WORK_START);
        assertThat(work.start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 23, 0));

        for (int i = 1; i < items.size(); i++) {
            assertThat(items.get(i).start()).isAfterOrEqualTo(items.get(i - 1).start());
        }

        assertInvariants(items, ShiftType.OFF, ShiftType.NIGHT);
    }

    @Test
    void OFF_TO_OFF_고정_템플릿() {
        List<TimelineItemDraft> items = place(ShiftType.OFF, ShiftType.OFF);

        assertThat(itemOf(items, ActivityKind.DAY_WAKE_UP).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 0));
        assertThat(itemOf(items, ActivityKind.BREAKFAST).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 7, 30));
        assertThat(itemOf(items, ActivityKind.DAYTIME_EXERCISE).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 10, 0));
        assertThat(itemOf(items, ActivityKind.LUNCH).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 12, 30));
        assertThat(itemOf(items, ActivityKind.DAYTIME_REST).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 15, 0));
        assertThat(itemOf(items, ActivityKind.DINNER).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 18, 30));
        assertThat(itemOf(items, ActivityKind.SLEEP_PREP).start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 22, 30));

        TimelineItemDraft sleep = itemOf(items, ActivityKind.NORMAL_SLEEP);
        assertThat(sleep.start()).isEqualTo(LocalDateTime.of(2026, 8, 17, 23, 0));
        assertThat(sleep.end()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));

        assertThat(items).extracting(TimelineItemDraft::kind).doesNotContain(ActivityKind.WORK_START);

        assertInvariants(items, ShiftType.OFF, ShiftType.OFF);
    }

    @Test
    void NIGHT_TO_DAY_usable_window_없음() {
        List<TimelineItemDraft> items = place(ShiftType.NIGHT, ShiftType.DAY);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).kind()).isEqualTo(ActivityKind.WORK_START);
        assertThat(items.get(0).start()).isEqualTo(LocalDateTime.of(2026, 8, 18, 7, 0));

        assertInvariants(items, ShiftType.NIGHT, ShiftType.DAY);
    }

    @Test
    void TIGHT_budget_대표_케이스_EVENING_TO_DAY_선택활동_제거() {
        TimelineRange range = rangeCalculator.calculate(date, ShiftType.EVENING, ShiftType.DAY, standardEnv());
        TimelineBudgetLevel budget = budgetClassifier.classify(range.timelineStart(), range.timelineEnd());
        assertThat(budget).isEqualTo(TimelineBudgetLevel.TIGHT);

        List<TimelineItemDraft> items = place(ShiftType.EVENING, ShiftType.DAY);

        assertThat(items).extracting(TimelineItemDraft::kind).doesNotContain(
                ActivityKind.POST_WORK_REST, ActivityKind.DAYTIME_EXERCISE, ActivityKind.DAYTIME_NAP
        );
        assertThat(items).extracting(TimelineItemDraft::kind).contains(
                ActivityKind.NORMAL_SLEEP, ActivityKind.PRE_WORK_MEAL, ActivityKind.PRE_WORK_PREPARATION
        );
    }
}
