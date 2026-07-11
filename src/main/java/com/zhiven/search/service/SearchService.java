package com.zhiven.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 关键词搜索服务 - 阶段 2 实现（如 Elasticsearch 集成）
 * 当前阶段：简单问题走 RAG fallback
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    public String search(String query) {
        log.debug("搜索服务暂未实现，query: {}", query);
        return null;
    }
}
