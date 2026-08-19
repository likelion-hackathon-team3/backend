package com.hackathon.backend.schedule.ocr;

import java.util.List;

// AiServer POST /api/ocr/schedule 원본 응답 전체를 그대로 역직렬화하기 위한 내부 전용 DTO.
// Backend 응답 명세와 모양이 다르므로(confidence/message 등) 외부에 노출하지 않는다.
record AiOcrResponse(boolean success, List<AiRecognizedSchedule> recognizedSchedules,
                      List<String> failedDates, String message) {
}
