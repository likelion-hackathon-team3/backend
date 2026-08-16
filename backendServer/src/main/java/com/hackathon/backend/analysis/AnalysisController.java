package com.hackathon.backend.analysis;

import com.hackathon.backend.analysis.dto.AnalysisResponse;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: GET /api/analysis (이 endpoint 하나뿐이다. request/query parameter 없음.)
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping
    public AnalysisResponse analyze() {
        return analysisService.analyze();
    }
}
