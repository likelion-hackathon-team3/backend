package com.hackathon.backend.timeline.ai;

import com.hackathon.backend.timeline.ActivityCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

// AiServer POST /api/timeline/generate 호출을 담당하는 얇은 클라이언트.
// 아래 경우 전부 예외를 던지지 않고 Optional.empty()로 통일해서, 호출하는 쪽(TimelineService)이
// 항상 기존 deterministic fallback으로 안전하게 내려갈 수 있게 한다.
// - aiserver.base-url 미설정(blank): HTTP 호출 자체를 시도하지 않는다(팀원이 실제 포트를
//   확정하기 전까지 backendServer 자기 자신 등 임의 기본값을 향해 호출하지 않기 위함).
// - timeout / connection 오류 / HTTP 오류 / JSON 파싱 실패: RestClientException으로 잡힌다.
// - 필수 필드 누락, 허용되지 않은 category: isValid()에서 걸러낸다.
@Component
public class AiTimelineClient {

    private static final Logger log = LoggerFactory.getLogger(AiTimelineClient.class);
    private static final String GENERATE_PATH = "/api/timeline/generate";

    private final RestClient restClient; // null이면 base-url 미설정 -> 항상 skip

    public AiTimelineClient(@Value("${aiserver.base-url:}") String baseUrl,
                            @Value("${aiserver.timeout-ms:3000}") int timeoutMs) {
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

    public Optional<AiTimelineResponse> generate(AiTimelineRequest request) {
        if (restClient == null) {
            return Optional.empty();
        }

        // 진단 로그: API key/Authorization 헤더 등 민감정보는 절대 남기지 않는다.
        // request(AiTimelineRequest) 자체도 통째로 찍지 않고 이미 응답 계약에 없는 값(민감할 수 있는
        // userNotes 등)까지 로그에 섞이지 않도록 필요한 필드만 개별적으로 남긴다.
        log.info("AiServer 호출 시작: targetDate={}, transitionType={}", request.targetDate(), request.transitionType());
        long startedAt = System.currentTimeMillis();

        try {
            AiTimelineResponse response = restClient.post()
                    .uri(GENERATE_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiTimelineResponse.class);

            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("AiServer 응답 수신 성공: elapsedMs={}", elapsedMs);

            boolean valid = isValid(response);
            if (!valid) {
                logInvalidResponse(response);
            }
            return valid ? Optional.of(response) : Optional.empty();
        } catch (RestClientException e) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.warn("AiServer 호출 실패: elapsedMs={}, exceptionClass={}, message={}",
                    elapsedMs, e.getClass().getName(), e.getMessage());
            return Optional.empty();
        }
    }

    // isValid()가 false를 반환하기 직전에 "왜" 무효였는지 진단용으로 남긴다.
    // isValid()/isValidItem()/isAllowedCategory() 자체의 판정 로직은 전혀 바꾸지 않는다.
    private void logInvalidResponse(AiTimelineResponse response) {
        if (response == null) {
            log.warn("AiServer 응답 검증 실패: response 자체가 null");
            return;
        }

        List<AiTimelineResponse.Item> items = response.timelineItems();
        log.warn("AiServer 응답 검증 실패: pageTitleBlank={}, pageSubtitleBlank={}, "
                        + "timelineItemsNull={}, timelineItemsCount={}, recommendationsNull={}, recommendationsCount={}",
                isBlank(response.pageTitle()), isBlank(response.pageSubtitle()),
                items == null, items == null ? 0 : items.size(),
                response.recommendations() == null,
                response.recommendations() == null ? 0 : response.recommendations().size());

        if (items == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            logInvalidItem(i, items.get(i));
        }
    }

    private void logInvalidItem(int index, AiTimelineResponse.Item item) {
        if (item == null) {
            log.warn("AiServer 응답 검증 실패: timelineItems[{}] 자체가 null", index);
            return;
        }
        if (isBlank(item.time()) || isBlank(item.title()) || isBlank(item.description()) || isBlank(item.category())) {
            log.warn("AiServer 응답 검증 실패: timelineItems[{}] 필드 blank - timeBlank={}, titleBlank={}, "
                            + "descriptionBlank={}, categoryBlank={}",
                    index, isBlank(item.time()), isBlank(item.title()),
                    isBlank(item.description()), isBlank(item.category()));
            return;
        }
        if (!isAllowedCategory(item.category())) {
            log.warn("AiServer 응답 검증 실패: timelineItems[{}]의 category가 허용되지 않음 - category={}",
                    index, item.category());
        }
    }

    private boolean isValid(AiTimelineResponse response) {
        if (response == null) {
            return false;
        }
        if (isBlank(response.pageTitle()) || isBlank(response.pageSubtitle())) {
            return false;
        }
        List<AiTimelineResponse.Item> items = response.timelineItems();
        if (items == null || response.recommendations() == null) {
            return false;
        }
        for (AiTimelineResponse.Item item : items) {
            if (!isValidItem(item)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidItem(AiTimelineResponse.Item item) {
        if (item == null) {
            return false;
        }
        if (isBlank(item.time()) || isBlank(item.title()) || isBlank(item.description()) || isBlank(item.category())) {
            return false;
        }
        return isAllowedCategory(item.category());
    }

    private boolean isAllowedCategory(String category) {
        try {
            ActivityCategory.valueOf(category);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
