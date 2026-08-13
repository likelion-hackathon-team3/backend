package com.likeLion.backend.aiserver.controller;

import com.likeLion.backend.aiserver.dto.ScheduleOcrResponse;
import com.likeLion.backend.aiserver.service.ScheduleOcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OCR", description = "근무표 사진 인식 API")
@RestController
@RequestMapping("/api/ocr")
public class ScheduleOcrController {

    private final ScheduleOcrService scheduleOcrService;

    public ScheduleOcrController(ScheduleOcrService scheduleOcrService) {
        this.scheduleOcrService = scheduleOcrService;
    }

    @Operation(
            summary = "근무표 사진 인식",
            description = "근무표 이미지를 분석하여 텍스트 데이터(날짜, 근무 유형)를 추출하고 구조화하여 반환합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "인식 처리 결과 반환 (성공/부분성공/실패)",
            content = @Content(schema = @Schema(implementation = ScheduleOcrResponse.class))
    )
    @PostMapping(value = "/schedule", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScheduleOcrResponse> recognizeSchedule(
            @RequestPart("file") MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(value = "userName", required = false) String userName,
            @org.springframework.web.bind.annotation.RequestParam(value = "scheduleType", required = false, defaultValue = "MULTI") com.likeLion.backend.aiserver.dto.ScheduleType scheduleType
    ) {
        ScheduleOcrResponse response = scheduleOcrService.processScheduleOcr(file, userName, scheduleType);
        return ResponseEntity.ok(response);
    }
}
