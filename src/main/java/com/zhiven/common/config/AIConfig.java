package com.zhiven.common.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    @Bean
    EmbeddingModel embeddingModel(@Value("${DASHSCOPE_API_KEY}") String apiKey) {
        OpenAiApi openAiApi = new OpenAiApi("https://dashscope.aliyuncs.com/compatible-mode", apiKey);
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .withModel("text-embedding-v3")
                .build();
        return new OpenAiEmbeddingModel(openAiApi, options);
    }

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return new SimpleVectorStore(embeddingModel);
    }
}
