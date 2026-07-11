# 知问 文档智能体 — 设计文档

> 2026-07-11 | 版本 1.0

## 项目概述

基于 Java + Spring Boot + Spring AI 的企业级智能文档处理平台。用户上传私有文档，通过自然语言进行精准问答、智能编辑、内容生成和任务委托。系统采用 RAG 架构检索文档内容，并通过 Agent 机制自主调用外部工具完成复杂任务。

---

## 一、技术栈

| 层 | 技术 |
|----|------|
| 框架 | Spring Boot 3.4+, Java 21 |
| LLM | DeepSeek (OpenAI 兼容协议) via Spring AI |
| 文档解析 | Apache Tika |
| 向量嵌入 | DeepSeek Embedding |
| 向量存储(阶段1) | Spring AI SimpleVectorStore |
| 向量存储(阶段2) | PostgreSQL + pgvector |
| 业务数据库 | PostgreSQL |
| 前端 | Thymeleaf + htmx + Tailwind CSS |
| 构建 | Maven |
| 部署架构 | 单体应用 |

---

## 二、模块划分

| 模块 | 职责 | 核心类 |
|------|------|--------|
| `auth` | 注册、登录、会话管理 | AuthController, UserService |
| `document` | 上传、解析、文档库管理 | DocumentController, DocIngestionService |
| `qa` | 问答路由、RAG 检索增强 | QAController, RAGService, QueryRouter |
| `agent` | Agent 任务编排、工具调用 | AgentService, ToolRegistry |
| `search` | 关键词搜索索引 | SearchService |
| `common` | 配置、异常、工具类 | DeepSeekConfig, GlobalExceptionHandler |

---

## 三、数据流

### 文档上链（Ingestion）

```
用户上传文件
  → DocController (接收 MultipartFile, 校验格式/大小)
  → DocIngestionService:
      1. Tika 解析 (PDF/Word/Excel/PPT → text)
      2. TokenTextSplitter (800 tokens/块, 重叠 100)
      3. EmbeddingClient (DeepSeek 向量化)
      4. VectorStore.accept (写入 SimpleVectorStore)
      5. 元数据入库 → PostgreSQL documents 表
```

约束：
- 单文件 20MB 上限
- 分块按 token 计数，非字符数
- 支持格式：PDF / DOCX / XLSX / PPTX / TXT / MD

### 问答检索（QA）

```
用户提问
  → QueryRouter 判断复杂度:
      - 简单问题 → SearchService (关键词搜索，后续实现)
      - 复杂问题 → RAGService:
          1. 问题向量化
          2. VectorStore.similaritySearch (Top-K=5)
          3. 构建 Prompt (上下文 + 问题)
          4. ChatClient.call
          5. 返回答案 + 引用来源
  → 记录 qa_history
```

### Agent 任务（阶段 1 预留框架）

```
用户委托任务
  → AgentService:
      1. ChatClient 解析意图
      2. ToolRegistry 调度工具
      3. 返回结果
```

阶段 1 实现 ToolRegistry 框架 + 2 个示例工具（文本摘要、格式转换）。

---

## 四、数据库表

### users
| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PK | |
| username | VARCHAR(50) UNIQUE | |
| password_hash | VARCHAR(255) | BCrypt |
| email | VARCHAR(100) | |
| created_at | TIMESTAMP | |

### documents
| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users | |
| filename | VARCHAR(255) | |
| file_type | VARCHAR(20) | pdf/docx/xlsx/pptx/txt/md |
| file_size | BIGINT | |
| chunk_count | INT | |
| status | VARCHAR(20) | UPLOADING/INDEXING/READY/FAILED |
| created_at | TIMESTAMP | |

### qa_history
| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users | |
| question | TEXT | |
| answer | TEXT | |
| sources | JSONB | [{doc_id, chunk_id, content}] |
| route | VARCHAR(10) | RAG/SEARCH |
| created_at | TIMESTAMP | |

### agent_tasks (预留)
| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGSERIAL PK | |
| user_id | BIGINT FK → users | |
| request | TEXT | |
| result | TEXT | |
| tools_used | JSONB | |
| status | VARCHAR(20) | PENDING/RUNNING/DONE/FAILED |
| created_at | TIMESTAMP | |

> 阶段 1 不建 chunk 表 — 向量数据存在 SimpleVectorStore 内存中，元数据存在向量对象内部。

---

## 五、路由设计

```
GET    /login                   登录页
POST   /login                   登录提交
GET    /register                注册页
POST   /register                注册提交
GET    /                        首页
GET    /docs                    文档列表
POST   /docs/upload             上传文档
GET    /docs/{id}               文档详情
POST   /docs/{id}/delete        删除文档
GET    /qa                      问答页面
POST   /qa/ask                  提问 (返回 htmx 片段)
GET    /agent                   任务委托页
POST   /agent/submit            提交 Agent 任务
GET    /history                 问答历史
```

---

## 六、安全策略

- Spring Security + 自定义 UserDetailsService
- BCrypt 密码加密
- Session 认证（非 JWT）
- 文档按 user_id 隔离
- 文件上传：格式白名单 + 20MB 限制
- CSRF 保护（Thymeleaf 自动注入）

---

## 七、配置要点

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        model: deepseek-chat
        options:
          temperature: 0.3
      embedding:
        model: deepseek-embedding  # 以 DeepSeek 实际模型名为准
  datasource:
    url: jdbc:postgresql://localhost:5432/zhiven
  servlet:
    multipart:
      max-file-size: 20MB
```

---

## 八、迁移路径

| 阶段 | 向量存储 | 目标 |
|------|---------|------|
| 阶段 1 | SimpleVectorStore | 跑通 RAG 全流程 |
| 阶段 2 | PostgreSQL + pgvector | 生产可用，支持持久化与规模化 |

迁移时替换 VectorStore Bean 实现即可，Service 层代码无感知。
