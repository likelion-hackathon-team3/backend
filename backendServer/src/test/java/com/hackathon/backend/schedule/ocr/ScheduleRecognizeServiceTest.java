package com.hackathon.backend.schedule.ocr;

import com.hackathon.backend.schedule.dto.RecognizedScheduleItem;
import com.hackathon.backend.schedule.dto.ScheduleRecognizeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ScheduleRecognizeService 검증. ScheduleOcrClient는 Mockito로 대체하고
// 실제 AiServer(Render 배포본)나 OpenAI 네트워크 호출은 전혀 하지 않는다.
@ExtendWith(MockitoExtension.class)
class ScheduleRecognizeServiceTest {

    @Mock
    private ScheduleOcrClient scheduleOcrClient;

    private ScheduleRecognizeService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleRecognizeService(scheduleOcrClient);
    }

    private MultipartFile imageFile() {
        return new MockMultipartFile("file", "schedule.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void 정상_성공이면_confidence_없이_recognizedSchedules를_반환한다() {
        AiOcrResponse aiResponse = new AiOcrResponse(true,
                List.of(new AiRecognizedSchedule("2026-08-10", "DAY", 100),
                        new AiRecognizedSchedule("2026-08-11", "NIGHT", 95)),
                List.of(), null);
        when(scheduleOcrClient.recognize(any())).thenReturn(Optional.of(aiResponse));

        ScheduleRecognizeResponse res = service.recognize(imageFile());

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getFailedDates()).isEmpty();
        assertThat(res.getRecognizedSchedules())
                .containsExactly(
                        new RecognizedScheduleItem("2026-08-10", "DAY"),
                        new RecognizedScheduleItem("2026-08-11", "NIGHT"));
    }

    @Test
    void 부분_성공이면_recognizedSchedules와_failedDates를_함께_반환한다() {
        AiOcrResponse aiResponse = new AiOcrResponse(true,
                List.of(new AiRecognizedSchedule("2026-08-10", "DAY", 100)),
                List.of("2026-08-12"), null);
        when(scheduleOcrClient.recognize(any())).thenReturn(Optional.of(aiResponse));

        ScheduleRecognizeResponse res = service.recognize(imageFile());

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getRecognizedSchedules()).containsExactly(new RecognizedScheduleItem("2026-08-10", "DAY"));
        assertThat(res.getFailedDates()).containsExactly("2026-08-12");
    }

    @Test
    void AiServer가_success_false를_반환하면_고정_실패_메시지로_변환한다() {
        AiOcrResponse aiResponse = new AiOcrResponse(false, null, null, "AiServer 내부 사유 메시지");
        when(scheduleOcrClient.recognize(any())).thenReturn(Optional.of(aiResponse));

        ScheduleRecognizeResponse res = service.recognize(imageFile());

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo(ScheduleRecognizeService.RECOGNITION_FAILED_MESSAGE);
        assertThat(res.getRecognizedSchedules()).isNull();
        assertThat(res.getFailedDates()).isNull();
    }

    @Test
    void AiServer_통신_실패_timeout_응답파싱실패면_고정_실패_메시지로_처리한다() {
        // ScheduleOcrClient는 통신 실패/timeout/파싱 실패를 모두 Optional.empty()로 통일해서 알려준다.
        when(scheduleOcrClient.recognize(any())).thenReturn(Optional.empty());

        ScheduleRecognizeResponse res = service.recognize(imageFile());

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo(ScheduleRecognizeService.RECOGNITION_FAILED_MESSAGE);
    }

    @Test
    void 파일이_없으면_AiServer를_호출하지_않고_바로_실패한다() {
        ScheduleRecognizeResponse res = service.recognize(null);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo(ScheduleRecognizeService.RECOGNITION_FAILED_MESSAGE);
        verifyNoInteractions(scheduleOcrClient);
    }

    @Test
    void 빈_파일이면_AiServer를_호출하지_않고_바로_실패한다() {
        MultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        ScheduleRecognizeResponse res = service.recognize(empty);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo(ScheduleRecognizeService.RECOGNITION_FAILED_MESSAGE);
        verifyNoInteractions(scheduleOcrClient);
    }
}
