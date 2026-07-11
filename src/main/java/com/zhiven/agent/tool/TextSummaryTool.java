package com.zhiven.agent.tool;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class TextSummaryTool implements Tool {

    private final ChatClient.Builder chatClientBuilder;

    public TextSummaryTool(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @Override
    public String name() {
        return "text_summary";
    }

    @Override
    public String description() {
        return "对给定文本进行摘要总结，提取关键信息";
    }

    @Override
    public String execute(String input) {
        ChatClient client = chatClientBuilder.build();
        return client.prompt()
                .user("请对以下文本进行摘要总结，提取3-5个关键要点：\n\n" + input)
                .call()
                .content();
    }
}
