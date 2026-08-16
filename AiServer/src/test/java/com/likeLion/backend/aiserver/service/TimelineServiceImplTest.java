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

        List<TimelineBlockDto> blocks = List.of(
                new TimelineBlockDto("15:00", "16:00", ActivityType.REST, "퇴근 후 휴식", "가벼운 휴식"),
                new TimelineBlockDto("18:00", "21:00", ActivityType.NAP, "사전 수면", "NIGHT 근무 전 필수 낮잠"),
                new TimelineBlockDto("23:00", "07:00", ActivityType.WORK, "NIGHT 근무", "야간 근무")
        );
        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "NIGHT 근무 전 충분한 사전 낮잠을 확보하세요!",
                blocks
        );

        given(timelineAiGenerator.generateFutureTimeline(any())).willReturn(rawResponse);

        // when
        TimelineGenerateResponse response = timelineService.generateTimeline(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.targetDate()).isEqualTo(targetDate);
        assertThat(response.mode()).isEqualTo(TimelineMode.FUTURE);
        assertThat(response.aiSummary()).contains("사전 낮잠");
        assertThat(response.timelineBlocks()).hasSize(3);
        verify(timelineAiGenerator).generateFutureTimeline(any());
    }

    @Test
    @DisplayName("analysisResult가 존재하면 TODAY 모드로 실시간 지표 반영 맞춤 타임라인을 생성한다")
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
                analysisResult
        );

        List<TimelineBlockDto> blocks = List.of(
                new TimelineBlockDto("23:00", "23:30", ActivityType.REST, "빠른 귀가 및 샤워", "스트레스 완화"),
                new TimelineBlockDto("23:45", "05:30", ActivityType.SLEEP, "집중 수면", "내일 DAY를 위한 조기 취침"),
                new TimelineBlockDto("07:00", "15:00", ActivityType.WORK, "DAY 근무", "주간 근무")
        );
        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse(
                "피로도가 높으므로 퇴근 후 바로 취침하여 5시간 반 이상의 수면을 확보하세요.",
                blocks
        );

        given(timelineAiGenerator.generateTodayTimeline(any())).willReturn(rawResponse);

        // when
        TimelineGenerateResponse response = timelineService.generateTimeline(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.targetDate()).isEqualTo(targetDate);
        assertThat(response.mode()).isEqualTo(TimelineMode.TODAY);
        assertThat(response.aiSummary()).contains("피로도가 높으므로");
        assertThat(response.timelineBlocks()).hasSize(3);
        verify(timelineAiGenerator).generateTodayTimeline(any());
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

        List<TimelineBlockDto> blocks = List.of(
                new TimelineBlockDto("08:30", "13:00", ActivityType.SLEEP, "퇴근 후 수면", "1차 수면")
        );
        RawTimelineAiResponse rawResponse = new RawTimelineAiResponse("NIGHT 퇴근 후 오전 수면으로 리듬을 되찾으세요.", blocks);
        given(timelineAiGenerator.generateFutureTimeline(any())).willReturn(rawResponse);

        // when
        TimelineGenerateResponse response = timelineService.generateTimeline(request);

        // then
        ArgumentCaptor<TimelineGenerateRequest> captor = ArgumentCaptor.forClass(TimelineGenerateRequest.class);
        verify(timelineAiGenerator).generateFutureTimeline(captor.capture());
        assertThat(captor.getValue().transitionType()).isEqualTo("NIGHT_TO_OFF");
        assertThat(response.targetDate()).isEqualTo(LocalDate.now());
    }
}
