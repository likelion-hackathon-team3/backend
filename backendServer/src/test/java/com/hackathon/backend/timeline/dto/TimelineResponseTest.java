package com.hackathon.backend.timeline.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// GET /api/timeline 응답의 JSON 직렬화 모양 테스트.
// 성공: { success, isFallback, data:{pageTitle,pageSubtitle,timelineItems[],recommendations[]} } (message 없음)
// 실패: { success, message } (isFallback/data 없음)
// timelineItems[]의 time/title/description/category는 기본 필드이고, highlight만 optional이다.
class TimelineResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void 성공_응답은_success_isFallback_data만_있고_message는_없다() {
        TimelineItemResponse item = new TimelineItemResponse(
                "23:30", "취침 준비", "조명을 어둡게 하고 휴대폰을 멀리 두세요.", "PREPARATION", null);
        TimelineData data = new TimelineData(
                "오늘의 회복 타임라인", "다음 근무까지 남은 시간", List.of(item), List.of("카페인은 피하세요."));
        TimelineResponse response = TimelineResponse.ok(data);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("isFallback").asBoolean()).isTrue();
        assertThat(json.has("message")).isFalse();

        JsonNode dataNode = json.get("data");
        assertThat(dataNode.get("pageTitle").asString()).isEqualTo("오늘의 회복 타임라인");
        assertThat(dataNode.get("pageSubtitle").asString()).isEqualTo("다음 근무까지 남은 시간");
        assertThat(dataNode.get("recommendations").get(0).asString()).isEqualTo("카페인은 피하세요.");

        JsonNode itemNode = dataNode.get("timelineItems").get(0);
        assertThat(itemNode.get("time").asString()).isEqualTo("23:30");
        assertThat(itemNode.get("title").asString()).isEqualTo("취침 준비");
        assertThat(itemNode.get("description").asString()).isEqualTo("조명을 어둡게 하고 휴대폰을 멀리 두세요.");
        assertThat(itemNode.get("category").asString()).isEqualTo("PREPARATION");
    }

    @Test
    void isFallback_false로_명시하면_그대로_직렬화된다() {
        TimelineItemResponse item = new TimelineItemResponse(
                "23:30", "취침 준비", "설명", "PREPARATION", null);
        TimelineData data = new TimelineData("제목", "부제목", List.of(item), List.of());
        TimelineResponse response = TimelineResponse.ok(data, false);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.get("isFallback").asBoolean()).isFalse();
        assertThat(json.has("message")).isFalse();
        assertThat(json.get("data").get("pageTitle").asString()).isEqualTo("제목");
    }

    @Test
    void highlight가_null이면_highlight만_응답에서_생략된다() {
        TimelineItemResponse item = new TimelineItemResponse(
                "07:00", "D 근무 시작", "파이팅! 오늘도 잘 해내요!", "WORK", null);
        TimelineData data = new TimelineData("제목", "부제목", List.of(item), List.of());
        TimelineResponse response = TimelineResponse.ok(data);

        JsonNode json = jsonMapper.valueToTree(response);
        JsonNode itemNode = json.get("data").get("timelineItems").get(0);

        assertThat(itemNode.get("time").asString()).isEqualTo("07:00");
        assertThat(itemNode.get("title").asString()).isEqualTo("D 근무 시작");
        assertThat(itemNode.get("description").asString()).isEqualTo("파이팅! 오늘도 잘 해내요!");
        assertThat(itemNode.get("category").asString()).isEqualTo("WORK");
        assertThat(itemNode.has("highlight")).isFalse();
    }

    @Test
    void highlight가_있으면_응답에_포함된다() {
        TimelineItemResponse item = new TimelineItemResponse(
                "22:30", "취침 준비", "설명", "PREPARATION", "오늘의 핵심 활동");
        TimelineData data = new TimelineData("제목", "부제목", List.of(item), List.of());
        TimelineResponse response = TimelineResponse.ok(data);

        JsonNode json = jsonMapper.valueToTree(response);
        JsonNode itemNode = json.get("data").get("timelineItems").get(0);

        assertThat(itemNode.get("highlight").asString()).isEqualTo("오늘의 핵심 활동");
    }

    @Test
    void 실패_응답에는_success_message만_있고_isFallback_data는_없다() {
        TimelineResponse response = TimelineResponse.fail("해당 날짜에 등록된 근무가 없습니다.");

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("message").asString()).isEqualTo("해당 날짜에 등록된 근무가 없습니다.");

        assertThat(json.has("isFallback")).isFalse();
        assertThat(json.has("data")).isFalse();
    }

    @Test
    void 계약에_없는_필드는_존재하지_않는다() {
        TimelineItemResponse item = new TimelineItemResponse(
                "07:00", "D 근무 시작", "파이팅! 오늘도 잘 해내요!", "WORK", null);
        TimelineData data = new TimelineData("제목", "부제목", List.of(item), List.of());
        TimelineResponse response = TimelineResponse.ok(data);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.has("transitionInfo")).isFalse();
        assertThat(json.has("weeklySummary")).isFalse();
        assertThat(json.has("wearableData")).isFalse();
        assertThat(json.has("targetDate")).isFalse();
        assertThat(json.has("mode")).isFalse();
        assertThat(json.has("date")).isFalse();

        JsonNode itemNode = json.get("data").get("timelineItems").get(0);
        assertThat(itemNode.has("categoryText")).isFalse();
    }
}
