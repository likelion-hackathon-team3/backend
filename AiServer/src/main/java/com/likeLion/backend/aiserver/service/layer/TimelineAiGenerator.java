package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.timeline.AnalysisResultDto;
import com.likeLion.backend.aiserver.dto.timeline.PersonalizationDto;
import com.likeLion.backend.aiserver.dto.timeline.RawTimelineAiResponse;
import com.likeLion.backend.aiserver.dto.timeline.ShiftTimesDto;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class TimelineAiGenerator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatModel chatModel;

    @Value("classpath:prompts/timeline_future.st")
    private Resource futurePromptResource;

    @Value("classpath:prompts/timeline_today.st")
    private Resource todayPromptResource;

    @Value("classpath:prompts/timeline_critic.st")
    private Resource criticPromptResource;

    @Value("${spring.ai.openai.chat.options.timeline-model:gpt-4o-mini}")
    private String timelineModel;

    public TimelineAiGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public RawTimelineAiResponse generateFutureTimeline(TimelineGenerateRequest request) {
        BeanOutputConverter<RawTimelineAiResponse> outputConverter = new BeanOutputConverter<>(RawTimelineAiResponse.class);
        Map<String, Object> modelMap = buildCommonModelMap(request, outputConverter);

        String promptTemplateString = loadResourceAsString(futurePromptResource);
        PromptTemplate template = new PromptTemplate(promptTemplateString);
        String promptText = template.render(modelMap);
        return executeSequentialPipeline(promptText, modelMap, outputConverter);
    }

    public RawTimelineAiResponse generateTodayTimeline(TimelineGenerateRequest request) {
        BeanOutputConverter<RawTimelineAiResponse> outputConverter = new BeanOutputConverter<>(RawTimelineAiResponse.class);
        Map<String, Object> modelMap = buildCommonModelMap(request, outputConverter);

        String currentTime = (request.currentTime() != null && !request.currentTime().isBlank())
                ? request.currentTime()
                : LocalTime.now().format(TIME_FORMATTER);
        modelMap.put("currentTime", currentTime);

        AnalysisResultDto analysis = request.analysisResult();
        if (analysis != null) {
            modelMap.put("riskLevel", analysis.riskLevelName());
            modelMap.put("recoveryStatus", analysis.recoveryStatusName());
            modelMap.put("fatigueLevel", analysis.fatigueLevelName());
            modelMap.put("availableHours", analysis.formattedAvailableHours());
            modelMap.put("consecutiveDays", analysis.formattedConsecutiveDays());
        } else {
            modelMap.put("riskLevel", "NORMAL");
            modelMap.put("recoveryStatus", "GOOD");
            modelMap.put("fatigueLevel", "LOW");
            modelMap.put("availableHours", "8.0");
            modelMap.put("consecutiveDays", "0");
        }

        String promptTemplateString = loadResourceAsString(todayPromptResource);
        PromptTemplate template = new PromptTemplate(promptTemplateString);
        String promptText = template.render(modelMap);
        return executeSequentialPipeline(promptText, modelMap, outputConverter);
    }

    private String loadResourceAsString(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template resource: " + resource.getFilename(), e);
        }
    }

    private Map<String, Object> buildCommonModelMap(TimelineGenerateRequest request, BeanOutputConverter<?> outputConverter) {
        Map<String, Object> map = new HashMap<>();
        map.put("targetDate", request.targetDate() != null ? request.targetDate().toString() : "");
        map.put("currentShift", request.currentShift() != null ? request.currentShift().name() : "OFF");
        map.put("nextShift", request.nextShift() != null ? request.nextShift().name() : "OFF");
        map.put("transitionType", request.transitionType() != null ? request.transitionType() : "OFF_TO_OFF");
        map.put("currentWorkEnd", (request.currentWorkEnd() != null && !request.currentWorkEnd().isBlank()) ? request.currentWorkEnd() : "해당 없음");
        map.put("nextWorkStart", (request.nextWorkStart() != null && !request.nextWorkStart().isBlank()) ? request.nextWorkStart() : "해당 없음");
        map.put("commuteMinutes", request.commuteMinutes() != null ? request.commuteMinutes() : 30);
        map.put("userNotes", (request.userNotes() != null && !request.userNotes().isBlank()) ? request.userNotes() : "없음");

        PersonalizationDto personalization = request.personalization();
        if (personalization != null) {
            map.put("recommendedSleepBuffer", String.valueOf(personalization.sleepBufferOrDefault()));
            map.put("adjustedCaffeineCutoff", personalization.caffeineCutoffOrDefault());
        } else {
            map.put("recommendedSleepBuffer", "0");
            map.put("adjustedCaffeineCutoff", "해당 없음");
        }

        ShiftTimesDto shiftTimes = request.shiftTimes();
        if (shiftTimes != null) {
            map.put("dayTime", shiftTimes.dayTimeOrDefault());
            map.put("eveningTime", shiftTimes.eveningTimeOrDefault());
            map.put("nightTime", shiftTimes.nightTimeOrDefault());
        } else {
            map.put("dayTime", ShiftTimesDto.DEFAULT_DAY_TIME);
            map.put("eveningTime", ShiftTimesDto.DEFAULT_EVENING_TIME);
            map.put("nightTime", ShiftTimesDto.DEFAULT_NIGHT_TIME);
        }

        map.put("format", outputConverter.getFormat());
        return map;
    }

    private RawTimelineAiResponse executeSequentialPipeline(String generatorPromptText, Map<String, Object> modelMap, BeanOutputConverter<RawTimelineAiResponse> outputConverter) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(timelineModel)
                .temperature(0.3)
                .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .build();

        // 1. Generator AI Call
        String draftJson = callModelForText(generatorPromptText, options);

        // 2. Critic AI Call
        modelMap.put("draftJson", draftJson);
        String criticPromptTemplateString = loadResourceAsString(criticPromptResource);
        PromptTemplate criticTemplate = new PromptTemplate(criticPromptTemplateString);
        String criticPromptText = criticTemplate.render(modelMap);

        String finalJson = callModelForText(criticPromptText, options);

        return outputConverter.convert(finalJson);
    }

    private String callModelForText(String promptText, OpenAiChatOptions options) {
        UserMessage message = UserMessage.builder()
                .text(promptText)
                .build();
        var response = chatModel.call(new Prompt(message, options));
        return response.getResult().getOutput().getText();
    }
}
