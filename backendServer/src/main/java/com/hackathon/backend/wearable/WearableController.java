package com.hackathon.backend.wearable;

import com.hackathon.backend.wearable.dto.WearableResponse;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: GET /api/wearable-data (이 endpoint 하나뿐이다. 신규 endpoint를 추가하지 않는다.)
@RestController
@RequestMapping("/api/wearable-data")
public class WearableController {

    private final WearableService wearableService;

    public WearableController(WearableService wearableService) {
        this.wearableService = wearableService;
    }

    // 현재 서버 실행 중 유지되는 웨어러블 목업 데이터를 반환한다.
    // 여러 번 호출해도 같은 실행 중에는 동일한 값이 나온다.
    @GetMapping
    public WearableResponse get() {
        WearableData data = wearableService.getCurrent();
        return new WearableResponse(data.sleepHours(), data.activityLevel(), data.heartRate());
    }
}
