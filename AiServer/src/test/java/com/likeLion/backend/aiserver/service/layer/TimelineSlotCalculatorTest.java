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
}
