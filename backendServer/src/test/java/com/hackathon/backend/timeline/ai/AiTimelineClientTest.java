package com.hackathon.backend.timeline.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// AiTimelineClient 검증. JDK 내장 HttpServer(새 의존성 없음)로 AiServer 역할을 흉내 내서
// 성공/HTTP 오류/타임아웃/JSON 깨짐/허용되지 않은 category/base-url 미설정을 검증한다.
// AiTimelineClient의 @Value 생성자 파라미터는 Spring 컨텍스트 밖에서 그냥 무시되므로
// Mock 서버 URL을 직접 넘겨 순수 자바 객체로 생성해서 테스트한다.
class AiTimelineClientTest {

    private static final String PATH = "/api/timeline/generate";

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(PATH, exchange -> {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private AiTimelineRequest sampleRequest() {
        return new AiTimelineRequest(
                "2026-08-17", "09:00", "DAY", "NIGHT", "DAY_TO_NIGHT",
                "2026-08-17T15:00", "2026-08-18T23:00", 30,
                new AiTimelineRequest.ShiftTimes("07:00", "15:00", "15:00", "23:00", "23:00", "07:00"),
                null, null, null
        );
    }

    @Test
    void base_url이_비어있으면_HTTP_시도_없이_empty를_반환한다() {
        AiTimelineClient client = new AiTimelineClient("", 3000);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void 정상_응답이면_AiTimelineResponse를_반환한다() throws IOException {
        String body = """
                {
                  "targetDate": "2026-08-17",
                  "mode": "TODAY",
                  "pageTitle": "AI 제목",
                  "pageSubtitle": "AI 부제목",
                  "timelineItems": [
                    {"time": "23:30", "title": "취침 준비", "description": "설명", "category": "PREPARATION", "highlight": null}
                  ],
                  "recommendations": ["추천1"]
                }
                """;
        String baseUrl = startServer(200, body, 0);
        AiTimelineClient client = new AiTimelineClient(baseUrl, 3000);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isPresent();
        assertThat(result.get().pageTitle()).isEqualTo("AI 제목");
        assertThat(result.get().timelineItems()).hasSize(1);
        assertThat(result.get().timelineItems().get(0).category()).isEqualTo("PREPARATION");
    }

    @Test
    void HTTP_5xx_응답이면_empty를_반환한다() throws IOException {
        String baseUrl = startServer(500, "{\"error\":\"internal\"}", 0);
        AiTimelineClient client = new AiTimelineClient(baseUrl, 3000);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void JSON_파싱에_실패하면_empty를_반환한다() throws IOException {
        String baseUrl = startServer(200, "{이건 유효한 JSON이 아님", 0);
        AiTimelineClient client = new AiTimelineClient(baseUrl, 3000);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void 필수_필드가_비어있으면_empty를_반환한다() throws IOException {
        String body = """
                {
                  "targetDate": "2026-08-17",
                  "mode": "TODAY",
                  "pageTitle": "",
                  "pageSubtitle": "AI 부제목",
                  "timelineItems": [],
                  "recommendations": []
                }
                """;
        String baseUrl = startServer(200, body, 0);
        AiTimelineClient client = new AiTimelineClient(baseUrl, 3000);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void 허용되지_않은_category이면_empty를_반환한다() throws IOException {
        String body = """
                {
                  "targetDate": "2026-08-17",
                  "mode": "TODAY",
                  "pageTitle": "AI 제목",
                  "pageSubtitle": "AI 부제목",
                  "timelineItems": [
                    {"time": "14:00", "title": "커피", "description": "설명", "category": "CAFFEINE", "highlight": null}
                  ],
                  "recommendations": []
                }
                """;
        String baseUrl = startServer(200, body, 0);
        AiTimelineClient client = new AiTimelineClient(baseUrl, 3000);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isEmpty();
    }

    @Test
    void 응답이_timeout보다_늦으면_empty를_반환한다() throws IOException {
        String body = """
                {
                  "targetDate": "2026-08-17",
                  "mode": "TODAY",
                  "pageTitle": "AI 제목",
                  "pageSubtitle": "AI 부제목",
                  "timelineItems": [],
                  "recommendations": []
                }
                """;
        String baseUrl = startServer(200, body, 1000);
        AiTimelineClient client = new AiTimelineClient(baseUrl, 200);

        Optional<AiTimelineResponse> result = client.generate(sampleRequest());

        assertThat(result).isEmpty();
    }
}
