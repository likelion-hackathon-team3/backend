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
    @DisplayName("POST /api/timeline/generate - currentTime, userNotes, 커스텀 shiftTimes, 실시간 분석 지표를 포함한 요청 정상 처리")
    void generateTimeline_todayMode_withUserNotes_success() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2026, 7, 12);
        ShiftTimesDto customShiftTimes = new ShiftTimesDto("06:30", "14:30", "14:30", "22:30", "22:30", "06:30");
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.EVENING,
                ShiftType.DAY,
                "EVENING_TO_DAY",
                "23:00",
                "카페인 민감, 암막커튼 사용",
                customShiftTimes,
                new AnalysisResultDto(RiskLevel.CAUTION, RecoveryStatus.RECOVERY_NEEDED, FatigueLevel.HIGH, 6.5, 2)
        );

        TimelineGenerateResponse response = new TimelineGenerateResponse(
                targetDate,
                TimelineMode.TODAY,
                "오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요",
                "회복을 최우선으로 한 개인 맞춤 루틴입니다.",
                List.of(
                        new TimelineItemDto("23:00", "저녁 식사", "단백질 위주의 가벼운 식사를 권장해요.", ActivityType.MEAL, null),
                        new TimelineItemDto("23:40", "취침 준비", "샤워 및 조명 낮추기", ActivityType.PREPARATION, null),
                        new TimelineItemDto("00:10", "취침", "수면 목표 5시간 20분", ActivityType.SLEEP, "권장 수면 시간: 5시간 20분"),
                        new TimelineItemDto("05:30", "기상", "물 한 잔과 스트레칭", ActivityType.WAKE_UP, null),
                        new TimelineItemDto("06:30", "D 근무 시작", "병원 도착 및 인수인계", ActivityType.WORK, null)
                ),
                List.of(
                        "오늘은 수면 확보가 가장 중요해요.",
                        "카페인은 14시 이후 섭취를 피해 주세요."
                )
        );

        given(timelineService.generateTimeline(any(TimelineGenerateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/timeline/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-07-12"))
                .andExpect(jsonPath("$.mode").value("TODAY"))
                .andExpect(jsonPath("$.pageTitle").value("오늘부터 내일 Day 근무 전까지의 맞춤 계획이에요"))
                .andExpect(jsonPath("$.timelineItems").isArray())
                .andExpect(jsonPath("$.timelineItems[0].time").value("23:00"))
                .andExpect(jsonPath("$.timelineItems[0].category").value("MEAL"))
                .andExpect(jsonPath("$.timelineItems[2].highlight").value("권장 수면 시간: 5시간 20분"));
    }

    @Test
    @DisplayName("POST /api/timeline/generate - 미래 날짜 요청 시 FUTURE 모드 응답 반환")
    void generateTimeline_futureMode_success() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2026, 7, 15);
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                targetDate,
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                null
        );

        TimelineGenerateResponse response = new TimelineGenerateResponse(
                targetDate,
                TimelineMode.FUTURE,
                "오늘부터 내일 Night 근무 전까지의 맞춤 계획이에요",
                "표준 교대 루틴에 맞춘 일정입니다.",
                List.of(
                        new TimelineItemDto("18:00", "사전 쪽잠", "야간 근무 전 필수 수면", ActivityType.NAP, "권장 낮잠: 2시간"),
                        new TimelineItemDto("23:00", "NIGHT 근무 시작", "야간 근무", ActivityType.WORK, null)
                ),
                List.of("출근 전 낮잠을 꼭 확보하세요.")
        );

        given(timelineService.generateTimeline(any(TimelineGenerateRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/timeline/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetDate").value("2026-07-15"))
                .andExpect(jsonPath("$.mode").value("FUTURE"))
                .andExpect(jsonPath("$.pageTitle").value("오늘부터 내일 Night 근무 전까지의 맞춤 계획이에요"))
                .andExpect(jsonPath("$.timelineItems[0].category").value("NAP"));
    }
}
