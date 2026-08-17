package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.timeline.AnalysisResultDto;
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

    public TimelineAiGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public RawTimelineAiResponse generateFutureTimeline(TimelineGenerateRequest request) {
        BeanOutputConverter<RawTimelineAiResponse> outputConverter = new BeanOutputConverter<>(RawTimelineAiResponse.class);
        Map<String, Object> modelMap = buildCommonModelMap(request, outputConverter);

        PromptTemplate template = new PromptTemplate(futurePromptResource);
        String promptText = template.render(modelMap);
        return callChatModel(promptText, outputConverter);
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

        PromptTemplate template = new PromptTemplate(todayPromptResource);
        String promptText = template.render(modelMap);
        return callChatModel(promptText, outputConverter);
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
