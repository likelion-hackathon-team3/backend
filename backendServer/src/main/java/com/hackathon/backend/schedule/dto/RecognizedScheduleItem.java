package com.hackathon.backend.schedule.dto;

// POST /api/schedules/recognize 응답에 담기는 인식된 근무 1건.
// AiServer 원본 응답의 confidence는 Backend 응답 명세에 없어 포함하지 않는다.
public record RecognizedScheduleItem(String date, String shift) {
}
