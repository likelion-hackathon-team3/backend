package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.timeline.AnalysisResultDto;
import com.likeLion.backend.aiserver.dto.timeline.RawTimelineAiResponse;
import com.likeLion.backend.aiserver.dto.timeline.TimelineGenerateRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TimelineAiGenerator {

    private final ChatModel chatModel;

    @Value("classpath:prompts/timeline_future.st")
    private Resource futurePromptResource;

    @Value("classpath:prompts/timeline_today.st")
    private Resource todayPromptResource;

    public TimelineAiGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public RawTimelineAiResponse generateFutureTimeline(TimelineGenerateRequest request) {
        BeanOutputConverter<RawTimelineAiResponse> outputConverter = new BeanOutputConverter<>(RawTimelineAiResponse.class);

        PromptTemplate template = new PromptTemplate(futurePromptResource);
        Map<String, Object> modelMap = new HashMap<>();
        modelMap.put("targetDate", request.targetDate() != null ? request.targetDate().toString() : "");
        modelMap.put("currentShift", request.currentShift() != null ? request.currentShift().name() : "OFF");
        modelMap.put("nextShift", request.nextShift() != null ? request.nextShift().name() : "OFF");
        modelMap.put("transitionType", request.transitionType() != null ? request.transitionType() : "OFF_TO_OFF");
        modelMap.put("format", outputConverter.getFormat());

        String promptText = template.render(modelMap);
        return callChatModel(promptText, outputConverter);
    }

    public RawTimelineAiResponse generateTodayTimeline(TimelineGenerateRequest request) {
        BeanOutputConverter<RawTimelineAiResponse> outputConverter = new BeanOutputConverter<>(RawTimelineAiResponse.class);

        PromptTemplate template = new PromptTemplate(todayPromptResource);
        Map<String, Object> modelMap = new HashMap<>();
        modelMap.put("targetDate", request.targetDate() != null ? request.targetDate().toString() : "");
        modelMap.put("currentShift", request.currentShift() != null ? request.currentShift().name() : "OFF");
        modelMap.put("nextShift", request.nextShift() != null ? request.nextShift().name() : "OFF");
        modelMap.put("transitionType", request.transitionType() != null ? request.transitionType() : "OFF_TO_OFF");

        AnalysisResultDto analysis = request.analysisResult();
        if (analysis != null) {
            modelMap.put("riskLevel", analysis.riskLevel() != null ? analysis.riskLevel().name() : "NORMAL");
            modelMap.put("recoveryStatus", analysis.recoveryStatus() != null ? analysis.recoveryStatus().name() : "GOOD");
            modelMap.put("fatigueLevel", analysis.fatigueLevel() != null ? analysis.fatigueLevel().name() : "LOW");
            modelMap.put("availableHours", analysis.availableHours() != null ? String.format("%.1f", analysis.availableHours()) : "8.0");
            modelMap.put("consecutiveDays", analysis.consecutiveDays() != null ? String.valueOf(analysis.consecutiveDays()) : "0");
        } else {
            modelMap.put("riskLevel", "NORMAL");
            modelMap.put("recoveryStatus", "GOOD");
            modelMap.put("fatigueLevel", "LOW");
            modelMap.put("availableHours", "8.0");
            modelMap.put("consecutiveDays", "0");
        }
        modelMap.put("format", outputConverter.getFormat());

        String promptText = template.render(modelMap);
        return callChatModel(promptText, outputConverter);
    }

    private RawTimelineAiResponse callChatModel(String promptText, BeanOutputConverter<RawTimelineAiResponse> outputConverter) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(promptText)
                .build();

        var response = chatModel.call(new Prompt(userMessage, options));
        String content = response.getResult().getOutput().getText();
        return outputConverter.convert(content);
    }
}
