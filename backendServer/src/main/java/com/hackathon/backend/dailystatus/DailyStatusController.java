package com.hackathon.backend.dailystatus;

import com.hackathon.backend.dailystatus.dto.*;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: POST /api/daily-status
@RestController
@RequestMapping("/api/daily-status")
public class DailyStatusController {

    private final DailyStatusService dailyStatusService;

    public DailyStatusController(DailyStatusService dailyStatusService) {
        this.dailyStatusService = dailyStatusService;
    }

    // 현재 피로도 입력 저장 (body 누락 시에도 500 대신 명세의 실패 응답으로 처리)
    @PostMapping
    public DailyStatusResponse save(@RequestBody(required = false) DailyStatusRequest request) {
        return dailyStatusService.save(request);
    }
}
