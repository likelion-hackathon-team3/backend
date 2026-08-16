package com.likeLion.backend.aiserver.controller;

import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateRequest;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateResponse;
import com.likeLion.backend.aiserver.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
@Tag(name = "AI Timeline API", description = "간호사 맞춤형 추천 타임라인 생성 API")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @PostMapping("/generate")
    @Operation(summary = "AI 맞춤형 추천 타임라인 생성",
            description = "근무 전환 정보 및 실시간 분석 지표(선택)를 기반으로 당일(TODAY) 또는 미래(FUTURE) 맞춤 일정을 생성합니다.")
    public ResponseEntity<TimelineGenerateResponse> generateTimeline(
            @RequestBody TimelineGenerateRequest request
    ) {
        TimelineGenerateResponse response = timelineService.generateTimeline(request);
        return ResponseEntity.ok(response);
    }
}
