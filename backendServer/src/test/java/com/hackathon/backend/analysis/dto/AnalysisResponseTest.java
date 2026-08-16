package com.hackathon.backend.analysis.dto;

import com.hackathon.backend.analysis.RecoveryStatus;
import com.hackathon.backend.dailystatus.FatigueLevel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// AnalysisResponse의 JSON 직렬화 모양 테스트.
// Spring Boot 4.1.0은 Jackson 3(jackson-databind 3.x)을 쓰며, jackson-core/jackson-databind의
// 패키지 루트가 tools.jackson.*로 바뀌었다(jackson-annotations만 com.fasterxml.jackson.annotation 유지 —
// AnalysisResponse.java의 @JsonInclude import는 그대로 유효하므로 수정하지 않는다).
// @JsonInclude(NON_NULL) 덕분에 성공/실패 응답에서 서로 다른 필드만 실제 JSON에 나타나는지 확인한다.
class AnalysisResponseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void 성공_응답에는_success_message_필드_자체가_없다() {
        CurrentConditionResponse condition =
                CurrentConditionResponse.of(FatigueLevel.HIGH, 4.5, RecoveryStatus.RECOVERY_NEEDED);
        AnalysisResponse response = AnalysisResponse.ok("EVENING_TO_DAY", 3, 7.7, 1320L, "DANGER", condition);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.has("success")).isFalse();
        assertThat(json.has("message")).isFalse();

        assertThat(json.get("transitionType").asString()).isEqualTo("EVENING_TO_DAY");
        assertThat(json.get("consecutiveDays").asInt()).isEqualTo(3);
        assertThat(json.get("availableHours").asDouble()).isEqualTo(7.7);
        assertThat(json.get("nextShiftMinutes").asLong()).isEqualTo(1320L);
        assertThat(json.get("riskLevel").asString()).isEqualTo("DANGER");
        assertThat(json.get("currentCondition").get("fatigueLevel").asString()).isEqualTo("높음");
    }

    @Test
    void 실패_응답에는_success_message만_있고_성공전용_필드는_없다() {
        AnalysisResponse response = AnalysisResponse.fail("근무 시간이 설정되지 않았습니다.");

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("message").asString()).isEqualTo("근무 시간이 설정되지 않았습니다.");

        assertThat(json.has("transitionType")).isFalse();
        assertThat(json.has("consecutiveDays")).isFalse();
        assertThat(json.has("availableHours")).isFalse();
        assertThat(json.has("nextShiftMinutes")).isFalse();
        assertThat(json.has("riskLevel")).isFalse();
        assertThat(json.has("currentCondition")).isFalse();
    }

    @Test
    void 다음_근무_없음_실패_메시지도_동일한_모양이다() {
        AnalysisResponse response = AnalysisResponse.fail("다음 근무 일정이 없습니다.");

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("message").asString()).isEqualTo("다음 근무 일정이 없습니다.");
        assertThat(json.has("riskLevel")).isFalse();
    }
}
