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
    @DisplayName("POST /api/timeline/generate 요청 시 타임라인 생성 결과를 200 OK와 함께 반환한다")
    void generateTimeline_success() throws Exception {
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
}
