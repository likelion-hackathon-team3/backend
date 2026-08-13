package com.hackathon.backend.wearable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// GET /api/wearable-data 응답 계약 테스트.
// - 정상 200 응답
// - JSON 필드가 정확히 sleepHours / activityLevel / heartRate 3개인지
// - fatigueLevel 등 명세 밖 필드가 없는지
// - 값이 허용 범위 안인지
// - 같은 실행 안에서 반복 호출 시 동일 값인지
@SpringBootTest
@AutoConfigureMockMvc
class WearableControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 정상_200_응답이고_세_필드가_허용_범위_안이다() throws Exception {
        mockMvc.perform(get("/api/wearable-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sleepHours").exists())
                .andExpect(jsonPath("$.activityLevel").exists())
                .andExpect(jsonPath("$.heartRate").exists())
                .andExpect(jsonPath("$.sleepHours", greaterThanOrEqualTo(4.0)))
                .andExpect(jsonPath("$.sleepHours", lessThanOrEqualTo(9.0)))
                .andExpect(jsonPath("$.activityLevel", greaterThanOrEqualTo(2000)))
                .andExpect(jsonPath("$.activityLevel", lessThanOrEqualTo(15000)))
                .andExpect(jsonPath("$.heartRate", greaterThanOrEqualTo(55)))
                .andExpect(jsonPath("$.heartRate", lessThanOrEqualTo(95)));
    }

    @Test
    void 응답_필드가_정확히_세_개이고_명세_밖_필드가_없다() throws Exception {
        mockMvc.perform(get("/api/wearable-data"))
                .andExpect(status().isOk())
                // 최상위 JSON 키가 정확히 3개인지
                .andExpect(jsonPath("$.*", hasSize(3)))
                // 명세 밖 필드가 없는지 (대표 후보들)
                .andExpect(jsonPath("$.fatigueLevel").doesNotExist())
                .andExpect(jsonPath("$.hrv").doesNotExist())
                .andExpect(jsonPath("$.deviceName").doesNotExist())
                .andExpect(jsonPath("$.deviceType").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.statusText").doesNotExist())
                .andExpect(jsonPath("$.riskLevel").doesNotExist())
                .andExpect(jsonPath("$.recoveryStatus").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void 같은_실행_안에서_반복_호출해도_같은_값을_반환한다() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/wearable-data"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/wearable-data"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }
}
