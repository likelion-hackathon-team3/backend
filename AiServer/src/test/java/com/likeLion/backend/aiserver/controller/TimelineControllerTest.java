package com.likeLion.backend.aiserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.*;
import com.likeLion.backend.aiserver.service.TimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TimelineControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private TimelineService timelineService;

    @InjectMocks
    private TimelineController timelineController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(timelineController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/timeline/generate - 당일 실시간 맞춤 요청 시 TODAY 모드 응답 반환")
    void generateTimeline_todayMode_success() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2026, 8, 17);
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                new AnalysisResultDto(RiskLevel.CAUTION, RecoveryStatus.RECOVERY_NEEDED, FatigueLevel.HIGH, 6.5, 2)
        );

        TimelineGenerateResponse response = new TimelineGenerateResponse(
                targetDate,
                TimelineMode.TODAY,
                "충분한 휴식을 취하세요.",
                List.of(
                        new TimelineBlockDto("18:00", "20:30", ActivityType.NAP, "낮잠", "수면 보충"),
                        new TimelineBlockDto("23:00", "07:00", ActivityType.WORK, "NIGHT 근무", "야간 근무")
                )
        );

        given(timelineService.generateTimeline(any(TimelineGenerateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/timeline/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-08-17"))
                .andExpect(jsonPath("$.mode").value("TODAY"))
                .andExpect(jsonPath("$.aiSummary").value("충분한 휴식을 취하세요."))
                .andExpect(jsonPath("$.timelineBlocks").isArray())
                .andExpect(jsonPath("$.timelineBlocks[0].activityType").value("NAP"))
                .andExpect(jsonPath("$.timelineBlocks[0].title").value("낮잠"));
    }

    @Test
    @DisplayName("POST /api/timeline/generate - 분석 결과 없는 미래 날짜 요청 시 FUTURE 모드 응답 반환")
    void generateTimeline_futureMode_success() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2026, 8, 22);
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.EVENING,
                ShiftType.DAY,
                "EVENING_TO_DAY",
                null
        );

        TimelineGenerateResponse response = new TimelineGenerateResponse(
                targetDate,
                TimelineMode.FUTURE,
                "내일 DAY 근무를 위해 오늘 밤 일찍 취침하세요.",
                List.of(
                        new TimelineBlockDto("23:00", "23:30", ActivityType.REST, "퇴근 후 샤워", "스트레스 완화"),
                        new TimelineBlockDto("24:00", "05:30", ActivityType.SLEEP, "취침", "조기 취침")
                )
        );

        given(timelineService.generateTimeline(any(TimelineGenerateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/timeline/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-08-22"))
                .andExpect(jsonPath("$.mode").value("FUTURE"))
                .andExpect(jsonPath("$.aiSummary").value("내일 DAY 근무를 위해 오늘 밤 일찍 취침하세요."))
                .andExpect(jsonPath("$.timelineBlocks").isArray())
                .andExpect(jsonPath("$.timelineBlocks[0].activityType").value("REST"))
                .andExpect(jsonPath("$.timelineBlocks[1].activityType").value("SLEEP"));
    }
}
