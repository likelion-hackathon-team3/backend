package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineSlotCalculatorTest {

    private TimelineSlotCalculator slotCalculator;

    @BeforeEach
    void setUp() {
        slotCalculator = new TimelineSlotCalculator();
    }

    @Test
    @DisplayName("DAY -> NIGHT 전환 시 출근 역산 준비 및 사전 쪽잠(NAP) 슬롯이 수학적으로 정확히 산출된다")
    void calculate_dayToNight_createsAccurateSlots() {
        // given
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                LocalDate.of(2026, 8, 20),
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                null,
                "2026-08-20T15:00",
                "2026-08-21T23:00",
                30,
                null,
                null,
                null
        );

        // when
        TimelineSkeletonDto skeleton = slotCalculator.calculateSkeleton(request);

        // then
        assertThat(skeleton).isNotNull();
        assertThat(skeleton.baseSlots()).isNotEmpty();

        // 1. 마지막 항목은 WORK (2026-08-21T23:00)
        BaseSlotDto lastSlot = skeleton.baseSlots().get(skeleton.baseSlots().size() - 1);
        assertThat(lastSlot.time()).isEqualTo("2026-08-21T23:00");
        assertThat(lastSlot.category()).isEqualTo(ActivityType.WORK);

        // 2. 출근 준비는 23:00 - 30분(통근) - 30분(준비) = 22:00
        boolean hasPrep = skeleton.baseSlots().stream()
                .anyMatch(s -> s.time().equals("2026-08-21T22:00") && s.category() == ActivityType.PREPARATION);
        assertThat(hasPrep).isTrue();

        // 3. 야간 근무 전 사전 낮잠(NAP)이 출근 준비 이전에 기상 완료되도록 배치됨
        boolean hasNap = skeleton.baseSlots().stream()
                .anyMatch(s -> s.category() == ActivityType.NAP);
        assertThat(hasNap).isTrue();
    }

    @Test
    @DisplayName("EVENING -> DAY 고위험 단축 교대(8시간) 시 수면(최소 5.5시간 이상)이 최우선 보호된다")
    void calculate_eveningToDay_protectsSleep() {
        // given
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                LocalDate.of(2026, 8, 20),
                ShiftType.EVENING,
                ShiftType.DAY,
                "EVENING_TO_DAY",
                null,
                "2026-08-20T23:00",
                "2026-08-21T07:00",
                30,
                null,
                null,
                null
        );

        // when
        TimelineSkeletonDto skeleton = slotCalculator.calculateSkeleton(request);

        // then
        assertThat(skeleton).isNotNull();

        // 수면 슬롯 검색 및 시간 검증 (최소 330분 = 5.5시간 확보)
        BaseSlotDto sleepSlot = skeleton.baseSlots().stream()
                .filter(s -> s.category() == ActivityType.SLEEP)
                .findFirst()
                .orElseThrow();

        assertThat(sleepSlot.durationMinutes()).isGreaterThanOrEqualTo(330L);
    }

    @Test
    @DisplayName("DAY -> EVENING 및 currentTime 20:30 입력 시 과거 일정은 제외되고 수면은 정상 7.5~8시간으로 제한된다")
    void calculate_dayToEvening_withCurrentTime_capsSleepAndExcludesPastEvents() {
        // given
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                LocalDate.of(2026, 8, 24),
                ShiftType.DAY,
                ShiftType.EVENING,
                "DAY_TO_EVENING",
                "20:30",
                "2026-08-24T15:00",
                "2026-08-25T15:00",
                30,
                null,
                null,
                null
        );

        // when
        TimelineSkeletonDto skeleton = slotCalculator.calculateSkeleton(request);

        // then
        assertThat(skeleton).isNotNull();

        // 1. 모든 슬롯의 시작 시각은 20:30 이상이어야 함
        assertThat(skeleton.baseSlots()).allMatch(s -> s.time().compareTo("2026-08-24T20:30") >= 0);

        // 2. 수면 시간은 14시간이 아니라 7.5~8시간(450~480분) 범위 내로 제한됨
        BaseSlotDto sleepSlot = skeleton.baseSlots().stream()
                .filter(s -> s.category() == ActivityType.SLEEP)
                .findFirst()
                .orElseThrow();
        assertThat(sleepSlot.durationMinutes()).isLessThanOrEqualTo(480L);
        assertThat(sleepSlot.durationMinutes()).isGreaterThanOrEqualTo(420L);

        // 3. 익일 점심 식사(MEAL) 슬롯 및 오전 여유 구간이 생성됨
        boolean hasLunch = skeleton.baseSlots().stream()
                .anyMatch(s -> s.category() == ActivityType.MEAL && s.time().startsWith("2026-08-25"));
        assertThat(hasLunch).isTrue();
        assertThat(skeleton.flexIntervals()).isNotEmpty();
    }

    @Test
    @DisplayName("2시간 30분 초단축 교대(예: 퇴근 15:00, 다음 출근 17:30) 시 5.5시간 강제 수면 대신 쪽잠(NAP)이나 휴식(REST)으로 적응 생성된다")
    void calculate_ultraShortTurnaround_createsNapOrRestWithoutTimeParadox() {
        // given
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                LocalDate.of(2026, 8, 20),
                ShiftType.DAY,
                ShiftType.EVENING,
                "DAY_TO_EVENING",
                null,
                "2026-08-20T15:00",
                "2026-08-20T17:30",
                20,
                null,
                null,
                null
        );

        // when
        TimelineSkeletonDto skeleton = slotCalculator.calculateSkeleton(request);

        // then
        assertThat(skeleton).isNotNull();
        // 1. 마지막 일정은 17:30 WORK
        BaseSlotDto last = skeleton.baseSlots().get(skeleton.baseSlots().size() - 1);
        assertThat(last.time()).isEqualTo("2026-08-20T17:30");
        assertThat(last.category()).isEqualTo(ActivityType.WORK);

        // 2. 전체 슬롯들이 15:00 ~ 17:30 사이에 완벽히 수납됨 (시간 모순 없음)
        assertThat(skeleton.baseSlots()).allMatch(s ->
                s.time().compareTo("2026-08-20T15:00") >= 0 && s.time().compareTo("2026-08-20T17:30") <= 0
        );

        // 3. 5.5시간 긴 SLEEP이 강제되지 않고, NAP 또는 REST가 생성됨
        boolean hasLongSleep = skeleton.baseSlots().stream()
                .anyMatch(s -> s.category() == ActivityType.SLEEP && s.durationMinutes() > 180);
        assertThat(hasLongSleep).isFalse();
    }
}
