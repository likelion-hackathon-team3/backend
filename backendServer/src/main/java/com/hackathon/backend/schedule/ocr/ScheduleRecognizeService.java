package com.hackathon.backend.schedule.ocr;

import com.hackathon.backend.schedule.dto.RecognizedScheduleItem;
import com.hackathon.backend.schedule.dto.ScheduleRecognizeResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

// POST /api/schedules/recognize 실제 로직을 담는 계층.
// AiServer 원본 응답(confidence/message 포함)을 Backend 응답 명세(date/shift만) 형태로 변환한다.
// 아래 경우 전부 동일한 고정 실패 응답으로 통일한다(AiServer가 보낸 개별 실패 사유 문구는 쓰지 않는다):
// - 업로드 파일 자체가 없거나 비어 있음
// - AiServer가 success=false를 반환
// - AiServer 미설정/통신 실패/timeout/응답 파싱 실패(ScheduleOcrClient가 Optional.empty()로 통일해서 알려줌)
@Service
public class ScheduleRecognizeService {

    static final String RECOGNITION_FAILED_MESSAGE = "이미지를 인식할 수 없습니다. 직접 입력해주세요.";

    private final ScheduleOcrClient scheduleOcrClient;

    public ScheduleRecognizeService(ScheduleOcrClient scheduleOcrClient) {
        this.scheduleOcrClient = scheduleOcrClient;
    }

    public ScheduleRecognizeResponse recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ScheduleRecognizeResponse.failure(RECOGNITION_FAILED_MESSAGE);
        }

        Optional<AiOcrResponse> aiResponse = scheduleOcrClient.recognize(file);
        if (aiResponse.isEmpty() || !aiResponse.get().success()) {
            return ScheduleRecognizeResponse.failure(RECOGNITION_FAILED_MESSAGE);
        }

        List<RecognizedScheduleItem> items = toItems(aiResponse.get().recognizedSchedules());
        List<String> failedDates = aiResponse.get().failedDates() == null
                ? List.of()
                : aiResponse.get().failedDates();

        return ScheduleRecognizeResponse.success(items, failedDates);
    }

    private List<RecognizedScheduleItem> toItems(List<AiRecognizedSchedule> recognizedSchedules) {
        if (recognizedSchedules == null) {
            return List.of();
        }
        return recognizedSchedules.stream()
                .map(item -> new RecognizedScheduleItem(item.date(), item.shift()))
                .toList();
    }
}
