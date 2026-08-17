package com.hackathon.backend.timeline;

import com.hackathon.backend.timeline.dto.TimelineResponse;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: GET /api/timeline (date 쿼리 파라미터, 생략 시 오늘).
// POST /api/timeline은 명세상 제거된 API이므로 만들지 않는다.
@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    public TimelineResponse getTimeline(@RequestParam(required = false) String date) {
        return timelineService.getTimeline(date);
    }
}
