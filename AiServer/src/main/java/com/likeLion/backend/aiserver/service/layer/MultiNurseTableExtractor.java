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
public class MultiNurseTableExtractor {

    private final ChatModel chatModel;

    @Value("classpath:prompts/multi-nurse-table.st")
    private Resource multiNursePromptResource;

    public MultiNurseTableExtractor(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public RawExtractionResponse extract(Media originalMedia, Media processedMedia, String userName, String coordinatesJson) {
        BeanOutputConverter<RawExtractionResponse> outputConverter = new BeanOutputConverter<>(RawExtractionResponse.class);

        String targetInstruction;
        if (userName != null && !userName.trim().isEmpty()) {
            targetInstruction = String.format("""
                2. 좌측 이름 목록에서 '%s' 간호사의 행(Row)을 정밀하게 찾으세요. 해당 이름 행이 존재하지 않는다면 전체 표 중 대표 1인 행을 파싱하세요.
                """, userName.trim());
        } else {
            targetInstruction = """
                2. 간호사 이름이 지정되지 않았으므로, 근무표에서 대표 1인의 행에서 날짜별 셀의 원문 글자를 파싱하세요.
                """;
        }

        PromptTemplate template = new PromptTemplate(multiNursePromptResource);
        String promptText = template.render(Map.of(
                "targetNurseInstruction", targetInstruction,
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
