package com.hackathon.backend.schedule.ocr;

// AiServer가 인식한 날짜별 근무 1건의 원본 표현.
// confidence는 Backend 응답 명세(date/shift만)에 없으므로 패키지 밖으로 노출하지 않는다
// (package-private로 막아 ScheduleRecognizeService 밖에서 실수로 그대로 반환하지 못하게 한다).
record AiRecognizedSchedule(String date, String shift, Integer confidence) {
}
