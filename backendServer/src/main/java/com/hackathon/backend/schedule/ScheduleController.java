package com.hackathon.backend.schedule;

import com.hackathon.backend.schedule.dto.*;
import com.hackathon.backend.schedule.ocr.ScheduleRecognizeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// HTTP 요청을 받아 Service로 넘기고, 결과를 JSON으로 돌려주는 계층.
// 담당 범위: POST/GET/DELETE /api/schedules, POST /api/schedules/recognize
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleRecognizeService scheduleRecognizeService;

    public ScheduleController(ScheduleService scheduleService, ScheduleRecognizeService scheduleRecognizeService) {
        this.scheduleService = scheduleService;
        this.scheduleRecognizeService = scheduleRecognizeService;
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

    // 근무표 사진 인식 (multipart/form-data, file 필드 필수)
    // required=false: FeedbackController.save()와 같은 이유로, file 파트가 아예 없어도
    // 400 예외 대신 명세의 실패 응답(success=false)으로 처리하기 위함.
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScheduleRecognizeResponse recognize(@RequestPart(value = "file", required = false) MultipartFile file) {
        return scheduleRecognizeService.recognize(file);
    }
}
