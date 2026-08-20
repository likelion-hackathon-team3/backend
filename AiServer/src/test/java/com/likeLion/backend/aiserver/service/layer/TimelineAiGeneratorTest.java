package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.ShiftType;
import com.likeLion.backend.aiserver.dto.timeline.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TimelineAiGeneratorTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private TimelineAiGenerator timelineAiGenerator;

    @BeforeEach
    void setUp() {
        String dummyPrompt = "Test template {targetDate} {transitionType} {recommendedSleepBuffer} {nextWorkStart} {commuteMinutes} {adjustedCaffeineCutoff} {totalFreeHours} {skeletonJson} {flexIntervals} {format}";
        ByteArrayResource resource = new ByteArrayResource(dummyPrompt.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(timelineAiGenerator, "futurePromptResource", resource);
        ReflectionTestUtils.setField(timelineAiGenerator, "todayPromptResource", resource);
        
        String criticPrompt = "Critic template {draftJson}";
        ByteArrayResource criticResource = new ByteArrayResource(criticPrompt.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(timelineAiGenerator, "criticPromptResource", criticResource);
        ReflectionTestUtils.setField(timelineAiGenerator, "slotCalculator", new TimelineSlotCalculator());
        ReflectionTestUtils.setField(timelineAiGenerator, "timelineModel", "gpt-4o-mini");
    }

    @Test
    @DisplayName("타임라인 생성 시 gpt-4o-mini 모델 및 JSON_OBJECT 포맷, temperature 0.3이 옵션으로 전달된다")
    void generateFutureTimeline_usesGpt4oMiniAndCorrectOptions() {
        // given
        TimelineGenerateRequest request = new TimelineGenerateRequest(
                LocalDate.of(2026, 8, 20),
                ShiftType.OFF,
                ShiftType.NIGHT,
                "OFF_TO_NIGHT",
                "20:00",
                "해당 없음",
                "2026-08-20T23:00",
                30,
                "조용한 수면",
                null,
                null
        );

        String brokenAiJson = """
                {
                    "pageTitle": "내일 Night 근무 전까지의 맞춤 계획이에요",
                    "pageSubtitle": "야간 근무 전 충분한 낮잠과 식사를 챙겨보세요.",
                    "timelineItems": [
                        {"time": "2026-08-20T23:00", "title": "NIGHT 근무 시작", "description": "야간 근무", "category": "WORK", "highlight": null},
                        {"time": "2026-08-20T21:30", "title": "기상", "description": "시간 역전 오류", "category": "WAKE_UP", "highlight": null}
                    ],
                    "recommendations": ["출근 전 30분 각성 시간을 확보하세요."]
                }
                """;

        String fixedAiJson = """
                {
                    "pageTitle": "내일 Night 근무 전까지의 맞춤 계획이에요",
                    "pageSubtitle": "야간 근무 전 충분한 낮잠과 식사를 챙겨보세요.",
                    "timelineItems": [
                        {"time": "2026-08-20T20:00", "title": "사전 낮잠", "description": "90분 낮잠", "category": "NAP", "highlight": "권장 낮잠: 1시간 30분"},
                        {"time": "2026-08-20T21:30", "title": "기상 및 식사", "description": "가벼운 식사", "category": "MEAL", "highlight": null},
                        {"time": "2026-08-20T22:00", "title": "출근 준비", "description": "샤워 및 환복", "category": "PREPARATION", "highlight": null},
                        {"time": "2026-08-20T22:30", "title": "출근 이동", "description": "병원 이동", "category": "REST", "highlight": null},
                        {"time": "2026-08-20T23:00", "title": "NIGHT 근무 시작", "description": "야간 근무", "category": "WORK", "highlight": null}
                    ],
                    "recommendations": ["출근 전 30분 각성 시간을 확보하세요."]
                }
                """;

        ChatResponse generatorResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(brokenAiJson))));
        ChatResponse criticResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(fixedAiJson))));
        given(chatModel.call(any(Prompt.class))).willReturn(generatorResponse, criticResponse);

        // when
        RawTimelineAiResponse response = timelineAiGenerator.generateFutureTimeline(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.pageTitle()).isEqualTo("내일 Night 근무 전까지의 맞춤 계획이에요");
        assertThat(response.timelineItems()).hasSize(5);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(2)).call(promptCaptor.capture());

        Prompt executedPrompt = promptCaptor.getAllValues().get(1);
        OpenAiChatOptions options = (OpenAiChatOptions) executedPrompt.getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(options.getTemperature()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("PersonalizationDto가 주어지면 프롬프트 렌더링에 수면 버퍼 및 카페인 컷오프 시각이 정확히 전달된다")
    void generateTodayTimeline_withPersonalization() {
        // given
        PersonalizationDto personalization = new PersonalizationDto(30, "14:30");

        TimelineGenerateRequest request = new TimelineGenerateRequest(
                LocalDate.of(2026, 8, 17),
                ShiftType.DAY,
                ShiftType.NIGHT,
                "DAY_TO_NIGHT",
                "16:00",
                "2026-08-17T15:00",
                "2026-08-18T23:00",
                30,
                "카페인 민감",
                null,
                personalization,
                null
        );

        String mockAiJson = """
                {
                    "pageTitle": "맞춤 타임라인",
                    "pageSubtitle": "피로 회복을 위해 수면 30분을 추가했습니다.",
                    "timelineItems": [],
                    "recommendations": ["14:30 이후 카페인 섭취를 중단하세요."]
                }
                """;

        org.springframework.ai.chat.messages.AssistantMessage assistantMessage = new org.springframework.ai.chat.messages.AssistantMessage(mockAiJson);
        Generation generation = new Generation(assistantMessage);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse, chatResponse);

        // when
        RawTimelineAiResponse response = timelineAiGenerator.generateTodayTimeline(request);

        // then
        assertThat(response).isNotNull();
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel, org.mockito.Mockito.times(2)).call(promptCaptor.capture());

        String renderedPrompt = promptCaptor.getAllValues().get(0).getContents();
        assertThat(renderedPrompt).contains("30");
        assertThat(renderedPrompt).contains("14:30");
        assertThat(renderedPrompt).contains("32시간");
    }
}
