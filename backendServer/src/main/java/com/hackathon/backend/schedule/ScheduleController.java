package com.hackathon.backend.schedule;

import com.hackathon.backend.schedule.dto.*;
import org.springframework.web.bind.annotation.*;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: POST/GET/DELETE /api/schedules
// (POST /api/schedules/recognize 는 다른 백엔드 담당이라 여기서 만들지 않는다.)
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    // 근무표 저장/수정 (AI 결과 확정, 수동 입력, 기존 일정 수정에 공통 사용)
    @PostMapping
    public ScheduleSaveResponse save(@RequestBody ScheduleSaveRequest request) {
        return scheduleService.save(request);
    }

    // 월별 근무표 조회 (예: /api/schedules?month=2026-08)
    @GetMapping
    public ScheduleListResponse getByMonth(@RequestParam String month) {
        return scheduleService.getByMonth(month);
    }

    // 특정 날짜 근무 삭제 (예: /api/schedules?date=2026-08-10)
    @DeleteMapping
    public ScheduleDeleteResponse delete(@RequestParam String date) {
        return scheduleService.delete(date);
    }
}
