package com.hackathon.backend.schedule.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

// AiServer POST /api/ocr/schedule 호출을 담당하는 얇은 클라이언트.
// AiTimelineClient와 같은 원칙: base-url 미설정 / 통신 오류 / timeout / 응답 파싱 실패는
// 예외를 던지지 않고 Optional.empty()로 통일해서, 호출하는 쪽(ScheduleRecognizeService)이
// 항상 안전하게 실패 응답으로 내려갈 수 있게 한다.
// base-url/timeout은 aiserver.base-url / aiserver.timeout-ms를 그대로 재사용한다
// (AiTimelineClient가 호출하는 것과 같은 AiServer 인스턴스를 가리키므로 별도 설정을 추가하지 않는다).
// scheduleType/userName은 프론트 명세에 없어 보내지 않는다 — AiServer가 scheduleType 생략 시
// MULTI로, userName 생략 시 null로 기본 처리하는 것을 계약 확인으로 검증했다.
@Component
public class ScheduleOcrClient {

    private static final Logger log = LoggerFactory.getLogger(ScheduleOcrClient.class);
    private static final String OCR_PATH = "/api/ocr/schedule";

    private final RestClient restClient; // null이면 base-url 미설정 -> 항상 skip

    public ScheduleOcrClient(@Value("${aiserver.base-url:}") String baseUrl,
                              @Value("${aiserver.timeout-ms:120000}") int timeoutMs) {
        this.restClient = (baseUrl == null || baseUrl.isBlank()) ? null : buildRestClient(baseUrl, timeoutMs);
    }

    private RestClient buildRestClient(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Optional<AiOcrResponse> recognize(MultipartFile file) {
        if (restClient == null) {
            return Optional.empty();
        }

        log.info("AiServer OCR 호출 시작: originalFilename={}, size={}", file.getOriginalFilename(), file.getSize());
        long startedAt = System.currentTimeMillis();

        try {
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", file.getResource());

            AiOcrResponse response = restClient.post()
                    .uri(OCR_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(AiOcrResponse.class);

            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("AiServer OCR 응답 수신 성공: elapsedMs={}", elapsedMs);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.warn("AiServer OCR 호출 실패: elapsedMs={}, exceptionClass={}, message={}",
                    elapsedMs, e.getClass().getName(), e.getMessage());
            return Optional.empty();
        }
    }
}
