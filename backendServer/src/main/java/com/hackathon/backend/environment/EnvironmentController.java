package com.hackathon.backend.environment;

import com.hackathon.backend.environment.dto.*;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: POST/GET /api/environment
@RestController
@RequestMapping("/api/environment")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    // 근무환경 저장/수정 (기존 설정이 있으면 갱신)
    @PostMapping
    public EnvironmentSaveResponse save(@RequestBody(required = false) EnvironmentSaveRequest request) {
        return environmentService.save(request);
    }

    // 저장된 근무환경 조회 (없으면 모든 값 null)
    @GetMapping
    public EnvironmentResponse get() {
        return environmentService.get();
    }
}
