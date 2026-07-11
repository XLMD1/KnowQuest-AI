package com.zhiven.qa.service;

public class QueryRouter {

    private QueryRouter() {}

    public static String route(String question) {
        if (question == null || question.isBlank()) {
            return "RAG";
        }
        String lower = question.toLowerCase().trim();
        if (lower.startsWith("最近") || lower.startsWith("最新的") || lower.startsWith("列出")) {
            return "SEARCH";
        }
        return "RAG";
    }
}
