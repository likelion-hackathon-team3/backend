package com.hackathon.backend.timeline;

import com.hackathon.backend.schedule.ShiftType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// AI 장애 시 사용하는 결정적(rule-based) fallback Timeline 아이템 배치기.
// 4개 그룹(WORK_TO_WORK/WORK_TO_OFF/OFF_TO_WORK/OFF_TO_OFF) 공통 로직에
// currentShift/nextShift가 NIGHT인지 여부만 최소로 추가해서 16개 전환을 처리한다.
// D/E/N 실제 시각은 TimelineRangeCalculator가 이미 ShiftDateTimeResolver로 계산해 넘겨준
// TimelineRange만 사용하고, 여기서 시각 규칙을 다시 계산하지 않는다.
//
// 이 클래스는 LocalDateTime.now()를 전혀 사용하지 않는 순수 결정적 계산기다.
// "오늘 날짜의 현재시각 기준 필터링"은 이 클래스의 책임이 아니라
// TimelineTodayFilter가 이 클래스의 결과물(전체 fallback)을 받아 후처리한다.
//
// 취침 시각은 곧장 이어붙이지 않고 22:30 전후(BEDTIME_ANCHOR)로 당겨서 배치한다.
// 단 그 시각을 이미 지났거나, 기다리면 최소 수면(4시간)조차 못 채우는 경우에는
// anchorOrImmediate()가 즉시 취침 준비로 넘어가 수면 확보를 우선한다.
// SLEEP과 그에 대응하는 WAKE_UP은 항상 시간상 바로 이어붙인다(SLEEP.end == WAKE_UP.start).
class TimelineItemPlacer {

    private static final int MEAL_MINUTES = 30;
    private static final int LIGHT_MEAL_MINUTES = 15;
    private static final int REST_MINUTES = 30;
    private static final int EXERCISE_MINUTES = 30;
    private static final int NAP_MINUTES = 90;
    private static final int SLEEP_PREP_MINUTES = 20;
    private static final int WAKE_UP_MINUTES = 10;
    private static final int PREPARATION_MINUTES = 20;

    private static final long MIN_SLEEP_MINUTES = 240; // 4시간
    private static final long MAX_SLEEP_MINUTES = 540;  // 9시간
    private static final long NIGHT_TO_NIGHT_SLEEP_CAP_MINUTES = 480; // 8시간
    private static final long NIGHT_TO_OFF_RECOVERY_SLEEP_CAP_MINUTES = 480; // 8시간

    private static final LocalTime BEDTIME_ANCHOR = LocalTime.of(22, 30);
    private static final LocalTime EVENING_MEAL_ANCHOR = LocalTime.of(18, 0);
    private static final long BEDTIME_MIN_ROOM_AFTER_CANDIDATE_MINUTES =
            SLEEP_PREP_MINUTES + WAKE_UP_MINUTES + MIN_SLEEP_MINUTES;

    private enum NightEntryStyle { FULL_OFF_DAY, POST_DAY_WORK, POST_EVENING_WORK }

    List<TimelineItemDraft> place(ShiftType currentShift, ShiftType nextShift,
                                   TimelineRange range, TimelineBudgetLevel budget) {
        if (!range.hasUsableWindow()) {
            List<TimelineItemDraft> items = new ArrayList<>();
            addWorkMarker(items, range);
            return items;
        }

        return switch (range.group()) {
            case WORK_TO_WORK -> buildWorkToWork(currentShift, nextShift, range, budget);
            case WORK_TO_OFF -> buildWorkToOff(currentShift, range, budget);
            case OFF_TO_WORK -> buildOffToWork(nextShift, range, budget);
            case OFF_TO_OFF -> buildOffToOff(range, budget);
        };
    }

    // ================= WORK_TO_WORK 분기 =================

    private List<TimelineItemDraft> buildWorkToWork(ShiftType current, ShiftType next,
                                                     TimelineRange range, TimelineBudgetLevel budget) {
        if (current == ShiftType.NIGHT && next == ShiftType.NIGHT) {
            return buildNightToNight(range, budget);
        }
        if (current == ShiftType.NIGHT) {
            return buildNightToNonNight(range, budget);
        }
        if (next == ShiftType.NIGHT) {
            NightEntryStyle style = current == ShiftType.DAY
                    ? NightEntryStyle.POST_DAY_WORK
                    : NightEntryStyle.POST_EVENING_WORK;
            return buildIntoNight(range, budget, style);
        }
        return buildNonNightToNonNight(range, budget);
    }

    // DAY/EVENING -> DAY/EVENING (NIGHT 미개입). 퇴근 직후 곧장 재우지 않고 22:30 전후로 당긴다.
    // SLEEP 직후 WAKE_UP을 정방향으로 이어붙이고, 근무 직전 MEAL/PREPARATION만 역방향으로 배치한다.
    private List<TimelineItemDraft> buildNonNightToNonNight(TimelineRange range, TimelineBudgetLevel budget) {
        List<TimelineItemDraft> forward = new ArrayList<>();
        Deque<TimelineItemDraft> backward = new ArrayDeque<>();

        LocalDateTime bwd = range.timelineEnd();
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_PREPARATION, PREPARATION_MINUTES);
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_MEAL,
                budget == TimelineBudgetLevel.TIGHT ? LIGHT_MEAL_MINUTES : MEAL_MINUTES);

        LocalDateTime fwd = range.timelineStart();
        fwd = advance(forward, fwd, ActivityKind.POST_WORK_MEAL, MEAL_MINUTES);
        if (budget != TimelineBudgetLevel.TIGHT && headroom(fwd, bwd) >= REST_MINUTES) {
            fwd = advance(forward, fwd, ActivityKind.POST_WORK_REST, REST_MINUTES);
        }
        if (budget == TimelineBudgetLevel.AMPLE && headroom(fwd, bwd) >= EXERCISE_MINUTES) {
            fwd = advance(forward, fwd, ActivityKind.DAYTIME_EXERCISE, EXERCISE_MINUTES);
        }

        placeSleepThenWakeUp(forward, fwd, bwd, ActivityKind.NORMAL_SLEEP);

        return assemble(forward, backward, range);
    }

    // NIGHT -> DAY/EVENING (usable window가 있는 경우만 호출됨). 선택 활동 없이 SLEEP을 최대화한다.
    private List<TimelineItemDraft> buildNightToNonNight(TimelineRange range, TimelineBudgetLevel budget) {
        List<TimelineItemDraft> forward = new ArrayList<>();
        Deque<TimelineItemDraft> backward = new ArrayDeque<>();

        LocalDateTime fwd = range.timelineStart();
        fwd = advance(forward, fwd, ActivityKind.POST_WORK_LIGHT_MEAL, LIGHT_MEAL_MINUTES);
        fwd = advance(forward, fwd, ActivityKind.SLEEP_PREP, SLEEP_PREP_MINUTES);

        LocalDateTime bwd = range.timelineEnd();
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_PREPARATION, PREPARATION_MINUTES);
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_MEAL, LIGHT_MEAL_MINUTES);
        bwd = retreat(backward, bwd, ActivityKind.POST_SLEEP_WAKE_UP, WAKE_UP_MINUTES);

        long sleepMinutes = clampSleep(minutesBetween(fwd, bwd));
        forward.add(new TimelineItemDraft(fwd, fwd.plusMinutes(sleepMinutes), ActivityKind.RECOVERY_SLEEP));

        return assemble(forward, backward, range);
    }

    // NIGHT -> NIGHT. 늦은 기상 + 기상 직후 MEAL + 다음 NIGHT 전 별도 MEAL(3끼 구조).
    private List<TimelineItemDraft> buildNightToNight(TimelineRange range, TimelineBudgetLevel budget) {
        List<TimelineItemDraft> forward = new ArrayList<>();
        Deque<TimelineItemDraft> backward = new ArrayDeque<>();

        LocalDateTime fwd = range.timelineStart();
        fwd = advance(forward, fwd, ActivityKind.POST_WORK_LIGHT_MEAL, LIGHT_MEAL_MINUTES);
        fwd = advance(forward, fwd, ActivityKind.SLEEP_PREP, SLEEP_PREP_MINUTES);

        LocalDateTime bwd = range.timelineEnd();
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_PREPARATION, PREPARATION_MINUTES);
        bwd = retreat(backward, bwd, ActivityKind.PRE_NIGHT_MEAL, MEAL_MINUTES);

        long tailAfterSleep = WAKE_UP_MINUTES + MEAL_MINUTES;
        long sleepMinutes = Math.max(0, Math.min(NIGHT_TO_NIGHT_SLEEP_CAP_MINUTES,
                minutesBetween(fwd, bwd) - tailAfterSleep));

        fwd = advance(forward, fwd, ActivityKind.RECOVERY_SLEEP, sleepMinutes);
        fwd = advance(forward, fwd, ActivityKind.POST_SLEEP_WAKE_UP, WAKE_UP_MINUTES);
        fwd = advance(forward, fwd, ActivityKind.POST_SLEEP_MEAL, MEAL_MINUTES);
        if (budget == TimelineBudgetLevel.AMPLE && headroom(fwd, bwd) >= REST_MINUTES) {
            advance(forward, fwd, ActivityKind.DAYTIME_REST, REST_MINUTES);
        }

        return assemble(forward, backward, range);
    }

    // DAY/EVENING/OFF -> NIGHT 공통: 정상 SLEEP(또는 OFF 기본 생활+SLEEP) + NIGHT 당일 사전 NAP.
    private List<TimelineItemDraft> buildIntoNight(TimelineRange range, TimelineBudgetLevel budget, NightEntryStyle style) {
        List<TimelineItemDraft> forward = new ArrayList<>();
        Deque<TimelineItemDraft> backward = new ArrayDeque<>();

        LocalDateTime bwd = range.timelineEnd();
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_PREPARATION, PREPARATION_MINUTES);
        bwd = retreat(backward, bwd, ActivityKind.PRE_NIGHT_MEAL, MEAL_MINUTES);

        LocalDateTime fwd;
        if (style == NightEntryStyle.FULL_OFF_DAY) {
            fwd = placeOffDayTemplate(forward, range.timelineStart(), bwd, budget);
        } else if (style == NightEntryStyle.POST_DAY_WORK) {
            fwd = range.timelineStart();
            if (budget != TimelineBudgetLevel.TIGHT) {
                fwd = advance(forward, fwd, ActivityKind.POST_WORK_REST, REST_MINUTES);
            }
            LocalDateTime mealStart = anchorOrImmediate(fwd, bwd, EVENING_MEAL_ANCHOR,
                    MEAL_MINUTES + BEDTIME_MIN_ROOM_AFTER_CANDIDATE_MINUTES);
            fwd = advance(forward, mealStart, ActivityKind.POST_WORK_MEAL, MEAL_MINUTES);
        } else {
            fwd = range.timelineStart();
            fwd = advance(forward, fwd, ActivityKind.POST_WORK_LIGHT_MEAL, LIGHT_MEAL_MINUTES);
        }

        LocalDateTime prepStart = anchorOrImmediate(fwd, bwd, BEDTIME_ANCHOR, BEDTIME_MIN_ROOM_AFTER_CANDIDATE_MINUTES);

        long remaining = minutesBetween(prepStart, bwd) - SLEEP_PREP_MINUTES;
        long tailAfterSleep = WAKE_UP_MINUTES + MEAL_MINUTES;
        long napWithWakeUp = NAP_MINUTES + WAKE_UP_MINUTES;

        long napMinutes;
        long sleepMinutes;
        if (remaining >= MIN_SLEEP_MINUTES + tailAfterSleep + napWithWakeUp) {
            napMinutes = NAP_MINUTES;
            sleepMinutes = Math.min(MAX_SLEEP_MINUTES, remaining - tailAfterSleep - napWithWakeUp);
        } else if (remaining >= MIN_SLEEP_MINUTES + tailAfterSleep + WAKE_UP_MINUTES + 30) {
            napMinutes = remaining - tailAfterSleep - MIN_SLEEP_MINUTES - WAKE_UP_MINUTES;
            sleepMinutes = MIN_SLEEP_MINUTES;
        } else {
            napMinutes = 0;
            sleepMinutes = Math.max(0, remaining - tailAfterSleep);
        }

        if (napMinutes > 0) {
            bwd = retreat(backward, bwd, ActivityKind.POST_NAP_WAKE_UP, WAKE_UP_MINUTES);
            retreat(backward, bwd, ActivityKind.PRE_NIGHT_NAP, napMinutes);
        }

        LocalDateTime sleepStart = advance(forward, prepStart, ActivityKind.SLEEP_PREP, SLEEP_PREP_MINUTES);
        LocalDateTime afterSleep = advance(forward, sleepStart, ActivityKind.NORMAL_SLEEP, sleepMinutes);
        afterSleep = advance(forward, afterSleep, ActivityKind.POST_SLEEP_WAKE_UP, WAKE_UP_MINUTES);
        LocalDateTime afterBreakfast = advance(forward, afterSleep, ActivityKind.POST_SLEEP_MEAL, MEAL_MINUTES);

        // 기상~낮잠 사이 빈 시간이 너무 길지 않도록 낮 시간 anchor를 추가한다(EVENING 진입은 제외:
        // 이미 22:30 이전에 즉시 취침으로 넘어가는 경우가 많아 이 구간 자체가 거의 없다).
        if (style != NightEntryStyle.POST_EVENING_WORK) {
            LocalDate wakeDate = afterBreakfast.toLocalDate();
            LocalDateTime dayFwd = placeAnchor(forward, wakeDate.atTime(12, 30), MEAL_MINUTES,
                    ActivityKind.LUNCH, bwd, afterBreakfast);
            if (budget != TimelineBudgetLevel.TIGHT) {
                dayFwd = placeAnchor(forward, wakeDate.atTime(15, 0), REST_MINUTES,
                        ActivityKind.DAYTIME_REST, bwd, dayFwd);
            }
            placeAnchor(forward, wakeDate.atTime(18, 30), MEAL_MINUTES, ActivityKind.DINNER, bwd, dayFwd);
        }

        return assemble(forward, backward, range);
    }

    // ================= WORK_TO_OFF =================

    private List<TimelineItemDraft> buildWorkToOff(ShiftType currentShift, TimelineRange range, TimelineBudgetLevel budget) {
        List<TimelineItemDraft> items = new ArrayList<>();
        LocalDateTime cursor = range.timelineStart();

        if (currentShift == ShiftType.NIGHT) {
            cursor = advance(items, cursor, ActivityKind.SLEEP_PREP, SLEEP_PREP_MINUTES);
            long remaining = minutesBetween(cursor, range.timelineEnd());
            long sleepMinutes = Math.min(remaining, NIGHT_TO_OFF_RECOVERY_SLEEP_CAP_MINUTES);

            cursor = advance(items, cursor, ActivityKind.RECOVERY_SLEEP, sleepMinutes);
            if (minutesBetween(cursor, range.timelineEnd()) >= WAKE_UP_MINUTES) {
                cursor = advance(items, cursor, ActivityKind.POST_SLEEP_WAKE_UP, WAKE_UP_MINUTES);
                if (minutesBetween(cursor, range.timelineEnd()) >= REST_MINUTES) {
                    cursor = advance(items, cursor, ActivityKind.DAYTIME_REST, REST_MINUTES);
                }
                if (minutesBetween(cursor, range.timelineEnd()) >= MEAL_MINUTES) {
                    LocalDateTime mealStart = anchorOrImmediate(cursor, range.timelineEnd(),
                            EVENING_MEAL_ANCHOR, MEAL_MINUTES);
                    advance(items, mealStart, ActivityKind.POST_SLEEP_MEAL, MEAL_MINUTES);
                }
            }
        } else {
            cursor = advance(items, cursor, ActivityKind.POST_WORK_MEAL, MEAL_MINUTES);
            if (budget != TimelineBudgetLevel.TIGHT) {
                cursor = advance(items, cursor, ActivityKind.POST_WORK_REST, REST_MINUTES);
            }
            LocalDateTime prepStart = anchorOrImmediate(cursor, range.timelineEnd(), BEDTIME_ANCHOR, BEDTIME_MIN_ROOM_AFTER_CANDIDATE_MINUTES);
            long sleepMinutes = clampSleep(minutesBetween(prepStart, range.timelineEnd()) - SLEEP_PREP_MINUTES);
            LocalDateTime sleepStart = advance(items, prepStart, ActivityKind.SLEEP_PREP, SLEEP_PREP_MINUTES);
            items.add(new TimelineItemDraft(sleepStart, sleepStart.plusMinutes(sleepMinutes), ActivityKind.NORMAL_SLEEP));
        }
        return items; // 다음 근무가 없는 그룹이라 WORK 마커 없음
    }

    // ================= OFF_TO_WORK =================

    private List<TimelineItemDraft> buildOffToWork(ShiftType nextShift, TimelineRange range, TimelineBudgetLevel budget) {
        if (nextShift == ShiftType.NIGHT) {
            return buildIntoNight(range, budget, NightEntryStyle.FULL_OFF_DAY);
        }
        return buildOffToWorkNonNight(range, budget);
    }

    private List<TimelineItemDraft> buildOffToWorkNonNight(TimelineRange range, TimelineBudgetLevel budget) {
        List<TimelineItemDraft> forward = new ArrayList<>();
        Deque<TimelineItemDraft> backward = new ArrayDeque<>();

        LocalDateTime bwd = range.timelineEnd();
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_PREPARATION, PREPARATION_MINUTES);
        bwd = retreat(backward, bwd, ActivityKind.PRE_WORK_MEAL,
                budget == TimelineBudgetLevel.TIGHT ? LIGHT_MEAL_MINUTES : MEAL_MINUTES);

        LocalDateTime fwd = placeOffDayTemplate(forward, range.timelineStart(), bwd, budget);

        placeSleepThenWakeUp(forward, fwd, bwd, ActivityKind.NORMAL_SLEEP);

        return assemble(forward, backward, range);
    }

    // ================= OFF_TO_OFF (고정 템플릿) =================

    private List<TimelineItemDraft> buildOffToOff(TimelineRange range, TimelineBudgetLevel budget) {
        LocalDateTime base = range.timelineStart();
        List<TimelineItemDraft> items = new ArrayList<>();

        items.add(fixedItem(base, WAKE_UP_MINUTES, ActivityKind.DAY_WAKE_UP));
        items.add(fixedItem(base.plusMinutes(30), MEAL_MINUTES, ActivityKind.BREAKFAST));
        if (budget != TimelineBudgetLevel.TIGHT) {
            items.add(fixedItem(base.plusMinutes(180), EXERCISE_MINUTES, ActivityKind.DAYTIME_EXERCISE));
        }
        items.add(fixedItem(base.plusMinutes(330), MEAL_MINUTES, ActivityKind.LUNCH));
        items.add(fixedItem(base.plusMinutes(480), REST_MINUTES, ActivityKind.DAYTIME_REST));
        items.add(fixedItem(base.plusMinutes(690), MEAL_MINUTES, ActivityKind.DINNER));
        items.add(fixedItem(base.plusMinutes(930), SLEEP_PREP_MINUTES, ActivityKind.SLEEP_PREP));

        LocalDateTime sleepStart = base.plusMinutes(960);
        items.add(new TimelineItemDraft(sleepStart, range.timelineEnd(), ActivityKind.NORMAL_SLEEP));
        return items; // 다음 근무가 없는 그룹이라 WORK 마커 없음
    }

    // ================= 공통 헬퍼 =================

    // OFF 하루 기본 생활을 07:00 기준 고정 anchor로 배치한다(OFF_TO_OFF와 동일한 시간대 사용).
    // 각 anchor가 hardLimit(역산 배치된 근무 전 블록의 시작 시각)을 넘기면 그 활동부터 생략한다.
    private LocalDateTime placeOffDayTemplate(List<TimelineItemDraft> items, LocalDateTime dayStart,
                                              LocalDateTime hardLimit, TimelineBudgetLevel budget) {
        LocalDateTime cursor = dayStart;
        cursor = placeAnchor(items, dayStart, WAKE_UP_MINUTES, ActivityKind.DAY_WAKE_UP, hardLimit, cursor);
        cursor = placeAnchor(items, dayStart.plusMinutes(30), MEAL_MINUTES, ActivityKind.BREAKFAST, hardLimit, cursor);
        if (budget != TimelineBudgetLevel.TIGHT) {
            cursor = placeAnchor(items, dayStart.plusMinutes(180), EXERCISE_MINUTES, ActivityKind.DAYTIME_EXERCISE, hardLimit, cursor);
        }
        cursor = placeAnchor(items, dayStart.plusMinutes(330), MEAL_MINUTES, ActivityKind.LUNCH, hardLimit, cursor);
        if (budget != TimelineBudgetLevel.TIGHT) {
            cursor = placeAnchor(items, dayStart.plusMinutes(480), REST_MINUTES, ActivityKind.DAYTIME_REST, hardLimit, cursor);
        }
        cursor = placeAnchor(items, dayStart.plusMinutes(690), MEAL_MINUTES, ActivityKind.DINNER, hardLimit, cursor);
        return cursor;
    }

    private LocalDateTime placeAnchor(List<TimelineItemDraft> items, LocalDateTime anchorStart, int minutes,
                                      ActivityKind kind, LocalDateTime hardLimit, LocalDateTime cursor) {
        LocalDateTime start = anchorStart.isBefore(cursor) ? cursor : anchorStart;
        LocalDateTime end = start.plusMinutes(minutes);
        if (end.isAfter(hardLimit)) {
            return cursor; // 시간이 부족하면 이 활동은 생략
        }
        items.add(new TimelineItemDraft(start, end, kind));
        return end;
    }

    // after 이후 같은 날 target까지 여유가 있으면 그 시각까지 활동 시작을 늦춘다.
    // 이미 그 시각을 지났거나, 기다리면 candidate 이후 minRoomAfterCandidate분조차 못 채우면
    // 지체 없이 after를 그대로 반환해 즉시 배치로 넘어간다(취침 준비/저녁 식사 앵커링 공용).
    private LocalDateTime anchorOrImmediate(LocalDateTime after, LocalDateTime hardLimit,
                                            LocalTime target, long minRoomAfterCandidate) {
        LocalDateTime sameDayTarget = after.toLocalDate().atTime(target);
        LocalDateTime candidate = after.isBefore(sameDayTarget) ? sameDayTarget : after;
        long roomAfterCandidate = minutesBetween(candidate, hardLimit);
        if (roomAfterCandidate < minRoomAfterCandidate) {
            return after;
        }
        return candidate;
    }

    // SLEEP_PREP -> SLEEP -> WAKE_UP을 배치한다. WAKE_UP은 항상 SLEEP 종료 직후로 이어붙여서
    // (SLEEP.end == WAKE_UP.start) 몇 시간씩 떨어지는 일이 없게 한다.
    // WAKE_UP 이후 bwd(근무 직전 블록 시작)까지 남는 시간은 비워둔다(필러 없음, 빈 시간 허용).
    private void placeSleepThenWakeUp(List<TimelineItemDraft> forward, LocalDateTime fwd, LocalDateTime bwd,
                                      ActivityKind sleepKind) {
        LocalDateTime prepStart = anchorOrImmediate(fwd, bwd, BEDTIME_ANCHOR, BEDTIME_MIN_ROOM_AFTER_CANDIDATE_MINUTES);
        long sleepMinutes = clampSleep(minutesBetween(prepStart, bwd) - SLEEP_PREP_MINUTES - WAKE_UP_MINUTES);
        LocalDateTime sleepStart = advance(forward, prepStart, ActivityKind.SLEEP_PREP, SLEEP_PREP_MINUTES);
        LocalDateTime sleepEnd = advance(forward, sleepStart, sleepKind, sleepMinutes);
        advance(forward, sleepEnd, ActivityKind.POST_SLEEP_WAKE_UP, WAKE_UP_MINUTES);
    }

    private List<TimelineItemDraft> assemble(List<TimelineItemDraft> forward, Deque<TimelineItemDraft> backward, TimelineRange range) {
        List<TimelineItemDraft> result = new ArrayList<>(forward);
        result.addAll(backward);
        addWorkMarker(result, range);
        return result;
    }

    private void addWorkMarker(List<TimelineItemDraft> items, TimelineRange range) {
        if (range.nextWorkActualStart() != null) {
            LocalDateTime at = range.nextWorkActualStart();
            items.add(new TimelineItemDraft(at, at, ActivityKind.WORK_START));
        }
    }

    private LocalDateTime advance(List<TimelineItemDraft> items, LocalDateTime cursor, ActivityKind kind, long minutes) {
        LocalDateTime end = cursor.plusMinutes(minutes);
        items.add(new TimelineItemDraft(cursor, end, kind));
        return end;
    }

    private LocalDateTime retreat(Deque<TimelineItemDraft> backward, LocalDateTime cursor, ActivityKind kind, long minutes) {
        LocalDateTime start = cursor.minusMinutes(minutes);
        backward.addFirst(new TimelineItemDraft(start, cursor, kind));
        return start;
    }

    private TimelineItemDraft fixedItem(LocalDateTime start, int minutes, ActivityKind kind) {
        return new TimelineItemDraft(start, start.plusMinutes(minutes), kind);
    }

    private long minutesBetween(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).toMinutes();
    }

    private long headroom(LocalDateTime fwd, LocalDateTime bwd) {
        return minutesBetween(fwd, bwd) - SLEEP_PREP_MINUTES - MIN_SLEEP_MINUTES;
    }

    private long clampSleep(long raw) {
        return Math.max(0, Math.min(raw, MAX_SLEEP_MINUTES));
    }
}
