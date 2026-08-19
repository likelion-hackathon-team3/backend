package com.hackathon.backend.dashboard;

import com.hackathon.backend.dashboard.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: GET /api/dashboard (사전예방 알림 조회)
// startDate/endDate는 Spring이 바로 LocalDate로 바인딩하게 하지 않고 String으로 받는다.
// 그래야 형식이 틀렸을 때 Spring 기본 400 대신 명세가 정한 실패 응답을 그대로 돌려줄 수 있다.
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard(@RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate) {
        return dashboardService.getDashboard(startDate, endDate);
    }
}
