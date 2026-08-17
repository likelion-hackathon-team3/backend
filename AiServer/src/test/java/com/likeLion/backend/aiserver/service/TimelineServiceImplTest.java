package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.*;
import com.likeLion.backend.aiserver.service.layer.TimelineAiGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TimelineServiceImplTest {

    @Mock
    private TimelineAiGenerator timelineAiGenerator;

    @InjectMocks
    private TimelineServiceImpl timelineService;

    @Test
    @DisplayName("analysisResult가 없으면 FUTURE 모드로 표준 권장 타임라인을 생성한다")
    void generateTimeline_futureMode() {
        // given
        LocalDate targetDate = LocalDate.of(2026, 8, 20);
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                null
        );

        List<TimelineItemDto> items = List.of(
                new TimelineItemDto("15:00", "퇴근 후 휴식", "가벼운 휴식", ActivityType.REST, null),
                new TimelineItemDto("18:00", "사전 수면", "NIGHT 근무 전 필수 낮잠", ActivityType.NAP, "권장 낮잠: 2시간"),
                new TimelineItemDto("23:00", "NIGHT 근무 시작", "야간 근무", ActivityType.WORK, null)
        );
        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "오늘부터 내일 Night 근무 전까지의 맞춤 계획이에요",
                "야간 근무 전 수면 확보를 최우선으로 합니다.",
                items,
                List.of("사전 낮잠을 꼭 확보하세요.", "야간 근무 전 수분 섭취를 늘리세요.")
        );

        given(timelineAiGenerator.generateFutureTimeline(any())).willReturn(rawResponse);

        // when
        TimelineGenerateResponse response = timelineService.generateTimeline(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.targetDate()).isEqualTo(targetDate);
        assertThat(response.mode()).isEqualTo(TimelineMode.FUTURE);
        assertThat(response.pageTitle()).contains("Night");
        assertThat(response.timelineItems()).hasSize(3);
        assertThat(response.recommendations()).hasSize(2);
        verify(timelineAiGenerator).generateFutureTimeline(any());
    }

    @Test
    @DisplayName("analysisResult, currentTime, userNotes가 존재하면 TODAY 모드로 맞춤 타임라인을 생성한다")
    void generateTimeline_todayMode() {
        // given
        LocalDate targetDate = LocalDate.of(2026, 8, 17);
        AnalysisResultDto analysisResult = new AnalysisResultDto(
                RiskLevel.CAUTION,
                RecoveryStatus.RECOVERY_NEEDED,
                FatigueLevel.HIGH,
                7.5,
                3
        );
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.EVENING,
                ShiftType.DAY,
                "EVENING_TO_DAY",
                "23:00",
                "카페인 민감, 암막커튼 사용",
                null,
                analysisResult
        );

        List<TimelineItemDto> items = List.of(
                new TimelineItemDto("23:30", "저녁 식사", "가벼운 식사", ActivityType.MEAL, null),
                new TimelineItemDto("00:10", "취침 준비", "샤워 및 조명 낮추기", ActivityType.PREPARATION, null),
                new TimelineItemDto("00:40", "취침", "수면 목표 5시간 10분", ActivityType.SLEEP, "권장 수면 시간: 5시간 10분"),
                new TimelineItemDto("05:50", "기상", "햇빛 쬐기", ActivityType.WAKE_UP, null),
                new TimelineItemDto("07:00", "DAY 근무 시작", "주간 근무", ActivityType.WORK, null)
        );
        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요",
                "피로도가 높은 날이에요. 회복을 최우선으로 한 개인 맞춤 루틴입니다.",
                items,
                List.of("오늘은 수면 확보가 가장 중요해요.", "카페인은 14시 이후 섭취를 피해 주세요.")
        );

        given(timelineAiGenerator.generateTodayTimeline(any())).willReturn(rawResponse);

        // when
        TimelineGenerateResponse response = timelineService.generateTimeline(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.targetDate()).isEqualTo(targetDate);
        assertThat(response.mode()).isEqualTo(TimelineMode.TODAY);
        assertThat(response.pageSubtitle()).contains("회복을 최우선");
        assertThat(response.timelineItems()).hasSize(5);
        assertThat(response.timelineItems().get(2).category()).isEqualTo(ActivityType.SLEEP);

        ArgumentCaptor<TimelineGenerateRequest> captor = ArgumentCaptor.forClass(TimelineGenerateRequest.class);
        verify(timelineAiGenerator).generateTodayTimeline(captor.capture());
        assertThat(captor.getValue().currentTime()).isEqualTo("23:00");
        assertThat(captor.getValue().userNotes()).isEqualTo("카페인 민감, 암막커튼 사용");
    }

    @Test
    @DisplayName("shiftTimes가 주어지면 커스텀 근무 시간대가 정상 전달된다")
    void generateTimeline_withCustomShiftTimes() {
        // given
        LocalDate targetDate = LocalDate.of(2026, 8, 20);
        ShiftTimesDto customShiftTimes = new ShiftTimesDto("06:30", "14:30", "14:30", "22:30", "22:30", "06:30");
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                "15:00",
                null,
                customShiftTimes,
                null
        );

        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "맞춤 계획", "서브타이틀", List.of(), List.of()
        );
        given(timelineAiGenerator.generateFutureTimeline(any())).willReturn(rawResponse);

        // when
        timelineService.generateTimeline(request);

        // then
        ArgumentCaptor<TimelineGenerateRequest> captor = ArgumentCaptor.forClass(TimelineGenerateRequest.class);
        verify(timelineAiGenerator).generateFutureTimeline(captor.capture());
        assertThat(captor.getValue().shiftTimes()).isNotNull();
        assertThat(captor.getValue().shiftTimes().dayStart()).isEqualTo("06:30");
        assertThat(captor.getValue().shiftTimes().dayEnd()).isEqualTo("14:30");
    }

    @Test
    @DisplayName("transitionType이 누락된 경우 currentShift와 nextShift로 자동 조합한다")
    void generateTimeline_autoGenerateTransitionType() {
        // given
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                null,
                ShiftType.NIGHT,
                ShiftType.OFF,
                null,
                null
        );

        List<TimelineItemDto> items = List.of(
                new TimelineItemDto("08:30", "퇴근 후 수면", "1차 수면", ActivityType.SLEEP, "권장 수면: 4시간 30분")
        );
        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "NIGHT 퇴근 후 OFF 일정 계획",
                "오전 수면으로 리듬을 회복하세요.",
                items,
                List.of("오후에는 햇볕을 쬐세요.")
        );
        given(timelineAiGenerator.generateFutureTimeline(any())).willReturn(rawResponse);

        // when
        TimelineGenerateResponse response = timelineService.generateTimeline(request);

        // then
        ArgumentCaptor<TimelineGenerateRequest> captor = ArgumentCaptor.forClass(TimelineGenerateRequest.class);
        verify(timelineAiGenerator).generateFutureTimeline(captor.capture());
        assertThat(captor.getValue().transitionType()).isEqualTo("NIGHT_TO_OFF");
        assertThat(response.targetDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("currentWorkEnd, nextWorkStart, commuteMinutes가 주어지면 정상 전달된다")
    void generateTimeline_withWorkEndAndStartAndCommute() {
        // given
        LocalDate targetDate = LocalDate.of(2026, 8, 17);
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                "00:15",
                "2026-08-17T15:00",
                "2026-08-18T23:00",
                45,
                "조용한 수면 선호",
                null,
                null
        );

        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "타이틀", "서브타이틀", List.of(), List.of()
        );
        given(timelineAiGenerator.generateFutureTimeline(any())).willReturn(rawResponse);

        // when
        timelineService.generateTimeline(request);

        // then
        ArgumentCaptor<TimelineGenerateRequest> captor = ArgumentCaptor.forClass(TimelineGenerateRequest.class);
        verify(timelineAiGenerator).generateFutureTimeline(captor.capture());
        assertThat(captor.getValue().currentWorkEnd()).isEqualTo("2026-08-17T15:00");
        assertThat(captor.getValue().nextWorkStart()).isEqualTo("2026-08-18T23:00");
        assertThat(captor.getValue().commuteMinutes()).isEqualTo(45);
    }
}
