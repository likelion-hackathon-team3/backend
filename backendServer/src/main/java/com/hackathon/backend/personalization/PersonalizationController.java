package com.hackathon.backend.personalization;

import com.hackathon.backend.personalization.dto.PersonalizationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: GET /api/personalization (개인화 보정값 조회)
// 외부 query parameter 이름은 프론트 호환을 위해 "shiftType"을 유지하지만, 값의 의미는 단일
// 근무유형이 아니라 전환유형(예: NIGHT_TO_OFF)이고 내부에서는 Feedback.transitionType과 비교한다.
// shiftType은 명세상 필수 파라미터이므로 required(기본값 true)로 받는다 — 누락 시 Spring 기본
// 400 처리를 그대로 사용하고, "축적된 피드백 없음" 기본 응답과 혼동하지 않는다.
// (값은 왔지만 동일 transitionType Feedback이 0개인 경우에만 Service가 그 기본 응답을 반환한다.)
@RestController
@RequestMapping("/api/personalization")
public class PersonalizationController {

    private final PersonalizationService personalizationService;

    public PersonalizationController(PersonalizationService personalizationService) {
        this.personalizationService = personalizationService;
    }

    @GetMapping
    public PersonalizationResponse getPersonalization(@RequestParam String shiftType) {
        return personalizationService.getPersonalization(shiftType);
    }
}
