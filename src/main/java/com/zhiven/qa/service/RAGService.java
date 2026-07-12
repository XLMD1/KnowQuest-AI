package com.zhiven.qa.service;

import com.zhiven.auth.entity.User;
import com.zhiven.qa.entity.QaHistory;
import com.zhiven.qa.repository.QaHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final QaHistoryRepository qaHistoryRepository;

    public RAGService(VectorStore vectorStore,
                      ChatClient.Builder chatClientBuilder,
                      QaHistoryRepository qaHistoryRepository) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.qaHistoryRepository = qaHistoryRepository;
    }

    public record Answer(String content, List<String> sources) {}

    public Answer ask(String question, User user) {
        // 阶段 1 SEARCH 路由走 RAG fallback（SearchService 占位）
        return ragAnswer(question, user);
    }

    private Answer ragAnswer(String question, User user) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.defaults()
                        .query(question)
                        .topK(5)
                        .filterExpression("user_id == " + user.getId())
                        .build()
        );

        if (docs.isEmpty()) {
            String fallback = "未找到与您问题相关的文档内容。请先上传相关文档。";
            saveHistory(user, question, fallback, "[]");
            return new Answer(fallback, List.of());
        }

        String context = docs.stream()
                .map(d -> "[" + d.getMetadata().get("filename") + "] " + d.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                你是一个智能文档助手。请严格基于以下文档内容回答用户问题。
                如果文档内容不足以回答问题，请如实说明，不要编造信息。

                文档内容：
                %s

                用户问题：%s

                请用中文回答，并在答案末尾列出引用的文档名称。
                """.formatted(context, question);

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        List<String> sources = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("filename", "unknown"))
                .distinct()
                .collect(Collectors.toList());

        saveHistory(user, question, answer, toSourcesJson(docs));

        log.info("RAG 问答完成: {} → {} chars", question, answer != null ? answer.length() : 0);
        return new Answer(answer, sources);
    }

    private void saveHistory(User user, String question, String answer, String sources) {
        QaHistory history = new QaHistory(user, question, answer, sources, "RAG");
        qaHistoryRepository.save(history);
    }

    private String toSourcesJson(List<Document> docs) {
        return docs.stream()
                .map(d -> String.format("{\"doc_id\":\"%s\",\"chunk_index\":\"%s\",\"content\":\"%s\"}",
                        escapeJson(String.valueOf(d.getMetadata().getOrDefault("doc_id", ""))),
                        escapeJson(String.valueOf(d.getMetadata().getOrDefault("chunk_index", ""))),
                        escapeJson(d.getContent() != null ? d.getContent().substring(0, Math.min(100, d.getContent().length())) : "")))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
