package com.hackathon.backend.timeline.ai;

import com.hackathon.backend.timeline.dto.TimelineData;
import com.hackathon.backend.timeline.dto.TimelineItemResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// AiTimelineResponse -> 기존 GET /api/timeline 응답 계약(TimelineData) 변환 검증.
class AiTimelineConverterTest {

    private final AiTimelineConverter converter = new AiTimelineConverter();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void pageTitle_pageSubtitle_recommendations가_그대로_매핑된다() {
        AiTimelineResponse response = new AiTimelineResponse(
                "2026-08-17", "TODAY", "AI 페이지 제목", "AI 부제목",
                List.of(new AiTimelineResponse.Item("07:00", "출근", "설명", "WORK", null)),
                List.of("추천1", "추천2")
        );

        TimelineData data = converter.convert(response);

        assertThat(data.getPageTitle()).isEqualTo("AI 페이지 제목");
        assertThat(data.getPageSubtitle()).isEqualTo("AI 부제목");
        assertThat(data.getRecommendations()).containsExactly("추천1", "추천2");
    }

    @Test
    void item의_time_title_description_category_highlight가_그대로_매핑된다() {
        AiTimelineResponse.Item item = new AiTimelineResponse.Item(
                "22:30", "취침 준비", "조명을 어둡게 하세요.", "PREPARATION", "오늘의 핵심 활동");
        AiTimelineResponse response = new AiTimelineResponse(
                "2026-08-17", "TODAY", "제목", "부제목", List.of(item), List.of());

        TimelineData data = converter.convert(response);
        TimelineItemResponse converted = data.getTimelineItems().get(0);

        assertThat(converted.getTime()).isEqualTo("22:30");
        assertThat(converted.getTitle()).isEqualTo("취침 준비");
        assertThat(converted.getDescription()).isEqualTo("조명을 어둡게 하세요.");
        assertThat(converted.getCategory()).isEqualTo("PREPARATION");
        assertThat(converted.getHighlight()).isEqualTo("오늘의 핵심 활동");
    }

    @Test
    void highlight가_null인_item도_정상적으로_변환된다() {
        AiTimelineResponse.Item item = new AiTimelineResponse.Item(
                "07:00", "출근", "파이팅!", "WORK", null);
        AiTimelineResponse response = new AiTimelineResponse(
                "2026-08-17", "TODAY", "제목", "부제목", List.of(item), List.of());

        TimelineData data = converter.convert(response);

        assertThat(data.getTimelineItems().get(0).getHighlight()).isNull();
    }

    @Test
    void timelineItems_순서는_AI_응답_순서_그대로_유지된다() {
        AiTimelineResponse.Item first = new AiTimelineResponse.Item("07:00", "첫번째", "d1", "WORK", null);
        AiTimelineResponse.Item second = new AiTimelineResponse.Item("12:00", "두번째", "d2", "MEAL", null);
        AiTimelineResponse.Item third = new AiTimelineResponse.Item("22:00", "세번째", "d3", "SLEEP", null);
        AiTimelineResponse response = new AiTimelineResponse(
                "2026-08-17", "TODAY", "제목", "부제목", List.of(first, second, third), List.of());

        TimelineData data = converter.convert(response);

        assertThat(data.getTimelineItems())
                .extracting(TimelineItemResponse::getTitle)
                .containsExactly("첫번째", "두번째", "세번째");
    }

    @Test
    void targetDate_mode는_frontend_TimelineData_JSON에_노출되지_않는다() {
        AiTimelineResponse response = new AiTimelineResponse(
                "2026-08-17", "TODAY", "제목", "부제목",
                List.of(new AiTimelineResponse.Item("07:00", "출근", "설명", "WORK", null)),
                List.of("추천")
        );

        TimelineData data = converter.convert(response);
        JsonNode json = jsonMapper.valueToTree(data);

        assertThat(json.has("targetDate")).isFalse();
        assertThat(json.has("mode")).isFalse();
        assertThat(json.get("pageTitle").asString()).isEqualTo("제목");
        assertThat(json.get("pageSubtitle").asString()).isEqualTo("부제목");
    }
}
