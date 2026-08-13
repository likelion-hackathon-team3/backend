package com.likeLion.backend.aiserver.service.layer;

import com.likeLion.backend.aiserver.dto.RawExtractionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PersonalCalendarExtractor {

    private final ChatModel chatModel;

    @Value("classpath:prompts/personal-calendar.st")
    private Resource personalCalendarPromptResource;

    public PersonalCalendarExtractor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public RawExtractionResponse extract(Media originalMedia, Media processedMedia, String coordinatesJson) {
        BeanOutputConverter<RawExtractionResponse> outputConverter = new BeanOutputConverter<>(RawExtractionResponse.class);

        PromptTemplate template = new PromptTemplate(personalCalendarPromptResource);
        String promptText = template.render(Map.of(
                "coordinates", coordinatesJson,
                "format", outputConverter.getFormat()
        ));

        org.springframework.ai.openai.OpenAiChatOptions options = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .responseFormat(org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.builder()
                        .type(org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                        .build())
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(promptText)
                .media(originalMedia, processedMedia)
                .build();

        var response = chatModel.call(new Prompt(userMessage, options));
        String content = response.getResult().getOutput().getText();
        return outputConverter.convert(content);
    }
}
