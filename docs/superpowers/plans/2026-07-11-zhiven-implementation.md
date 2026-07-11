# 知问 文档智能体 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零搭建知问文档智能体平台，包含用户认证、文档上传/RAG 问答、Agent 工具调用。

**Architecture:** Spring Boot 单体应用，6 个模块包（auth/document/qa/agent/search/common），Thymeleaf + htmx 服务端渲染，Spring AI 接入 DeepSeek。

**Tech Stack:** Spring Boot 3.4+, Java 21, Spring AI 1.0.0, PostgreSQL, Apache Tika, Thymeleaf + htmx + Tailwind CSS, Maven

## Global Constraints

- Java 21, Spring Boot 3.4+, Maven 构建
- DeepSeek API（OpenAI 兼容协议）via Spring AI OpenAI Starter
- SimpleVectorStore 内存向量存储（阶段1）
- 密码 BCrypt 加密，Session 认证
- 文件上传上限 20MB
- 分块策略：TokenTextSplitter 800 tokens/块，重叠 100
- 支持格式：PDF/DOCX/XLSX/PPTX/TXT/MD
- 文档按 user_id 隔离

---

## 文件结构总览

```
KnowQuest AI/
├── pom.xml
├── src/main/java/com/zhiven/
│   ├── ZhivenApplication.java
│   ├── common/
│   │   ├── config/SecurityConfig.java
│   │   └── exception/GlobalExceptionHandler.java
│   ├── auth/
│   │   ├── entity/User.java
│   │   ├── repository/UserRepository.java
│   │   ├── service/UserService.java
│   │   └── controller/AuthController.java
│   ├── document/
│   │   ├── entity/Document.java
│   │   ├── repository/DocumentRepository.java
│   │   ├── service/DocIngestionService.java
│   │   └── controller/DocumentController.java
│   ├── qa/
│   │   ├── entity/QaHistory.java
│   │   ├── repository/QaHistoryRepository.java
│   │   ├── service/QueryRouter.java
│   │   ├── service/RAGService.java
│   │   └── controller/QAController.java
│   ├── agent/
│   │   ├── entity/AgentTask.java
│   │   ├── repository/AgentTaskRepository.java
│   │   ├── tool/Tool.java
│   │   ├── tool/ToolRegistry.java
│   │   ├── tool/TextSummaryTool.java
│   │   ├── tool/FormatConvertTool.java
│   │   ├── service/AgentService.java
│   │   └── controller/AgentController.java
│   ├── search/service/SearchService.java
│   └── history/controller/HistoryController.java
├── src/main/resources/
│   ├── application.yml
│   ├── templates/
│   │   ├── layout.html
│   │   ├── login.html
│   │   ├── register.html
│   │   ├── index.html
│   │   ├── docs.html
│   │   ├── qa.html
│   │   ├── agent.html
│   │   └── history.html
│   └── static/css/style.css
└── src/test/java/com/zhiven/
    └── ZhivenApplicationTests.java
```

---

### Task 1: 项目骨架

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/zhiven/ZhivenApplication.java`
- Create: `src/test/java/com/zhiven/ZhivenApplicationTests.java`
- Create: `src/test/resources/application-test.yml`
- Create: `.gitignore`

**Interfaces:**
- Produces: Spring Boot 应用可启动，PostgreSQL 连接就绪，Spring AI + DeepSeek 配置就绪

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <groupId>com.zhiven</groupId>
    <artifactId>zhiven</artifactId>
    <version>0.1.0</version>
    <name>知问 文档智能体</name>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.0.0-M5</spring-ai.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.thymeleaf.extras</groupId>
            <artifactId>thymeleaf-extras-springsecurity6</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-tika-document-reader</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 application.yml**

```yaml
spring:
  application:
    name: zhiven
  datasource:
    url: jdbc:postgresql://localhost:5432/zhiven
    username: ${DB_USER:zhiven}
    password: ${DB_PASSWORD:zhiven}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.3
      embedding:
        options:
          model: deepseek-embedding

server:
  port: 8080

logging:
  level:
    com.zhiven: DEBUG
```

- [ ] **Step 3: 创建 application-test.yml**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:zhiven_test;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
  ai:
    openai:
      api-key: test-key
```

- [ ] **Step 4: 创建 ZhivenApplication.java**

```java
package com.zhiven;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ZhivenApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhivenApplication.class, args);
    }
}
```

- [ ] **Step 5: 创建 ZhivenApplicationTests.java**

```java
package com.zhiven;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ZhivenApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: 创建 .gitignore**

```
target/
*.class
*.jar
*.log
.idea/
*.iml
.env
application-local.yml
```

- [ ] **Step 7: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: 初始化 Git 并提交**

```bash
git init
git add .
git commit -m "chore: 初始化项目骨架"
```

---

### Task 2: 公共配置（安全 + 异常处理）

**Files:**
- Create: `src/main/java/com/zhiven/common/config/SecurityConfig.java`
- Create: `src/main/java/com/zhiven/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: 无（仅依赖 Spring Boot 自动配置）
- Produces:
  - `SecurityConfig` — SecurityFilterChain, PasswordEncoder Bean
  - `GlobalExceptionHandler` — 全局异常拦截，返回错误页面片段

- [ ] **Step 1: 创建 SecurityConfig.java**

```java
package com.zhiven.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/css/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/qa/ask", "/agent/submit")
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

> 注意：CSRF 对 `/qa/ask` 和 `/agent/submit` 放行，因为 htmx POST 将在请求头中手动携带 CSRF token。

- [ ] **Step 2: 创建 GlobalExceptionHandler.java**

```java
package com.zhiven.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ModelAndView handleMaxUploadSize() {
        ModelAndView mav = new ModelAndView("fragments/error");
        mav.addObject("message", "文件大小超过 20MB 限制");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        ModelAndView mav = new ModelAndView("fragments/error");
        mav.addObject("message", "服务器内部错误，请稍后重试");
        return mav;
    }
}
```

- [ ] **Step 3: 创建错误片段模板 `src/main/resources/templates/fragments/error.html`**

```html
<div th:fragment="error" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
    <p th:text="${message}"></p>
</div>
```

- [ ] **Step 4: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "chore: 添加安全配置和全局异常处理"
```

---

### Task 3: JPA 实体 + 仓库

**Files:**
- Create: `src/main/java/com/zhiven/auth/entity/User.java`
- Create: `src/main/java/com/zhiven/auth/repository/UserRepository.java`
- Create: `src/main/java/com/zhiven/document/entity/Document.java`
- Create: `src/main/java/com/zhiven/document/repository/DocumentRepository.java`
- Create: `src/main/java/com/zhiven/qa/entity/QaHistory.java`
- Create: `src/main/java/com/zhiven/qa/repository/QaHistoryRepository.java`
- Create: `src/main/java/com/zhiven/agent/entity/AgentTask.java`
- Create: `src/main/java/com/zhiven/agent/repository/AgentTaskRepository.java`

**Interfaces:**
- Consumes: Task 2 (SecurityConfig for BCrypt)
- Produces: 所有 JPA Entity 和 Repository 接口，供后续 Service 层使用

- [ ] **Step 1: 创建 User.java**

```java
package com.zhiven.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String email;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public User(String username, String passwordHash, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: 创建 UserRepository.java**

```java
package com.zhiven.auth.repository;

import com.zhiven.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

- [ ] **Step 3: 创建 Document.java**

```java
package com.zhiven.document.entity;

import com.zhiven.auth.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String filename;

    @Column(name = "file_type", length = 20)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Document() {}

    public Document(User user, String filename, String fileType, Long fileSize) {
        this.user = user;
        this.filename = filename;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: 创建 DocumentRepository.java**

```java
package com.zhiven.document.repository;

import com.zhiven.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserId(Long userId);
}
```

- [ ] **Step 5: 创建 QaHistory.java**

```java
package com.zhiven.qa.entity;

import com.zhiven.auth.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "qa_history")
public class QaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "JSONB")
    private String sources;

    @Column(length = 10)
    private String route;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public QaHistory() {}

    public QaHistory(User user, String question, String answer, String sources, String route) {
        this.user = user;
        this.question = question;
        this.answer = answer;
        this.sources = sources;
        this.route = route;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getSources() { return sources; }
    public void setSources(String sources) { this.sources = sources; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 6: 创建 QaHistoryRepository.java**

```java
package com.zhiven.qa.repository;

import com.zhiven.qa.entity.QaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QaHistoryRepository extends JpaRepository<QaHistory, Long> {
    List<QaHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 7: 创建 AgentTask.java**

```java
package com.zhiven.agent.entity;

import com.zhiven.auth.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_tasks")
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String request;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "tools_used", columnDefinition = "JSONB")
    private String toolsUsed;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AgentTask() {}

    public AgentTask(User user, String request) {
        this.user = user;
        this.request = request;
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getRequest() { return request; }
    public void setRequest(String request) { this.request = request; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getToolsUsed() { return toolsUsed; }
    public void setToolsUsed(String toolsUsed) { this.toolsUsed = toolsUsed; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 8: 创建 AgentTaskRepository.java**

```java
package com.zhiven.agent.repository;

import com.zhiven.agent.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
    List<AgentTask> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 9: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: 提交**

```bash
git add .
git commit -m "feat: 添加JPA实体和仓库层"
```

---

### Task 4: 认证模块

**Files:**
- Create: `src/main/java/com/zhiven/auth/service/UserService.java`
- Create: `src/main/java/com/zhiven/auth/controller/AuthController.java`
- Create: `src/main/resources/templates/login.html`
- Create: `src/main/resources/templates/register.html`

**Interfaces:**
- Consumes: `UserRepository` (Task 3), `PasswordEncoder` (Task 2)
- Produces: `UserService` — `register(username, password, email)` 返回 `User`，供 Task 5+ 使用

- [ ] **Step 1: 创建 UserService.java**

```java
package com.zhiven.auth.service;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String rawPassword, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User(username, passwordEncoder.encode(rawPassword), email);
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                new ArrayList<>()
        );
    }
}
```

- [ ] **Step 2: 创建 AuthController.java**

```java
package com.zhiven.auth.controller;

import com.zhiven.auth.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email,
            RedirectAttributes redirectAttributes) {
        try {
            userService.register(username, password, email);
            redirectAttributes.addFlashAttribute("success", "注册成功，请登录");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }
}
```

- [ ] **Step 3: 创建 login.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>登录 - 知问</title>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-50 min-h-screen flex items-center justify-center">
    <div class="bg-white p-8 rounded-lg shadow-md w-full max-w-md">
        <h1 class="text-2xl font-bold text-center text-gray-800 mb-6">知问 文档智能体</h1>

        <div th:if="${param.logout}" class="bg-green-50 text-green-700 px-4 py-2 rounded mb-4 text-sm">
            已退出登录
        </div>
        <div th:if="${success}" th:text="${success}" class="bg-green-50 text-green-700 px-4 py-2 rounded mb-4 text-sm"></div>
        <div th:if="${param.error}" class="bg-red-50 text-red-700 px-4 py-2 rounded mb-4 text-sm">
            用户名或密码错误
        </div>

        <form th:action="@{/login}" method="post">
            <div class="mb-4">
                <label class="block text-gray-700 text-sm font-medium mb-1">用户名</label>
                <input type="text" name="username" required
                       class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
            <div class="mb-6">
                <label class="block text-gray-700 text-sm font-medium mb-1">密码</label>
                <input type="password" name="password" required
                       class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
            <button type="submit"
                    class="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 transition">
                登录
            </button>
        </form>
        <p class="text-center text-sm text-gray-600 mt-4">
            没有账号？<a href="/register" class="text-blue-600 hover:underline">注册</a>
        </p>
    </div>
</body>
</html>
```

- [ ] **Step 4: 创建 register.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>注册 - 知问</title>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-50 min-h-screen flex items-center justify-center">
    <div class="bg-white p-8 rounded-lg shadow-md w-full max-w-md">
        <h1 class="text-2xl font-bold text-center text-gray-800 mb-6">创建账号</h1>

        <div th:if="${error}" th:text="${error}" class="bg-red-50 text-red-700 px-4 py-2 rounded mb-4 text-sm"></div>

        <form th:action="@{/register}" method="post">
            <div class="mb-4">
                <label class="block text-gray-700 text-sm font-medium mb-1">用户名</label>
                <input type="text" name="username" required
                       class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
            <div class="mb-4">
                <label class="block text-gray-700 text-sm font-medium mb-1">邮箱</label>
                <input type="email" name="email" required
                       class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
            <div class="mb-6">
                <label class="block text-gray-700 text-sm font-medium mb-1">密码</label>
                <input type="password" name="password" required minlength="6"
                       class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500">
            </div>
            <button type="submit"
                    class="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 transition">
                注册
            </button>
        </form>
        <p class="text-center text-sm text-gray-600 mt-4">
            已有账号？<a href="/login" class="text-blue-600 hover:underline">登录</a>
        </p>
    </div>
</body>
</html>
```

- [ ] **Step 5: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add .
git commit -m "feat: 添加用户注册登录模块"
```

---

### Task 5: 文档模块

**Files:**
- Create: `src/main/java/com/zhiven/document/service/DocIngestionService.java`
- Create: `src/main/java/com/zhiven/document/controller/DocumentController.java`
- Create: `src/main/resources/templates/docs.html`
- Create: `src/main/resources/templates/fragments/doc-list.html`

**Interfaces:**
- Consumes: `DocumentRepository` (Task 3), `UserService` (Task 4), `VectorStore`, `EmbeddingClient` (Spring AI 自动注入)
- Produces: `DocIngestionService` — `ingest(MultipartFile, User)` 返回 `Document`

- [ ] **Step 1: 创建 DocIngestionService.java**

```java
package com.zhiven.document.service;

import com.zhiven.auth.entity.User;
import com.zhiven.document.entity.Document;
import com.zhiven.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocIngestionService.class);
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "docx", "xlsx", "pptx", "txt", "md");

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    private final DocumentRepository documentRepository;

    public DocIngestionService(VectorStore vectorStore,
                               EmbeddingClient embeddingClient,
                               DocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
        this.documentRepository = documentRepository;
    }

    public Document ingest(MultipartFile file, User user) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("不支持的文件格式: " + extension);
        }

        Document doc = new Document(user, originalFilename, extension, file.getSize());
        doc.setStatus("INDEXING");
        documentRepository.save(doc);

        try {
            DocumentReader reader = new TikaDocumentReader(
                    new InputStreamResource(file.getInputStream()));
            List<org.springframework.ai.document.Document> rawDocs = reader.get();

            TokenTextSplitter splitter = new TokenTextSplitter(800, 100, 10, 5000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(rawDocs);

            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).getMetadata().put("doc_id", doc.getId());
                chunks.get(i).getMetadata().put("chunk_index", i);
                chunks.get(i).getMetadata().put("filename", originalFilename);
                chunks.get(i).getMetadata().put("user_id", user.getId());
            }

            vectorStore.add(chunks);

            doc.setChunkCount(chunks.size());
            doc.setStatus("READY");
            log.info("文档上链完成: {} ({} 块)", originalFilename, chunks.size());
        } catch (Exception e) {
            doc.setStatus("FAILED");
            log.error("文档处理失败: {}", originalFilename, e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }

        return documentRepository.save(doc);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
```

- [ ] **Step 2: 创建 DocumentController.java**

```java
package com.zhiven.document.controller;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import com.zhiven.document.entity.Document;
import com.zhiven.document.repository.DocumentRepository;
import com.zhiven.document.service.DocIngestionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/docs")
public class DocumentController {

    private final DocIngestionService ingestionService;
    private final DocumentRepository documentRepository;
    private final UserService userService;

    public DocumentController(DocIngestionService ingestionService,
                              DocumentRepository documentRepository,
                              UserService userService) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        List<Document> docs = documentRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("documents", docs);
        return "docs";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择文件");
            return "redirect:/docs";
        }
        try {
            User user = userService.findByUsername(principal.getName());
            ingestionService.ingest(file, user);
            redirectAttributes.addFlashAttribute("success", "上传成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/docs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal,
                         RedirectAttributes redirectAttributes) {
        User user = userService.findByUsername(principal.getName());
        documentRepository.findById(id).ifPresentOrElse(doc -> {
            if (!doc.getUser().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error", "无权删除");
                return;
            }
            documentRepository.delete(doc);
            redirectAttributes.addFlashAttribute("success", "删除成功");
        }, () -> redirectAttributes.addFlashAttribute("error", "文档不存在"));
        return "redirect:/docs";
    }
}
```

- [ ] **Step 3: 创建 docs.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>文档管理 - 知问</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <div th:replace="~{layout :: navbar}"></div>

    <main class="max-w-4xl mx-auto px-4 py-8">
        <div class="flex justify-between items-center mb-6">
            <h1 class="text-xl font-bold text-gray-800">文档管理</h1>
        </div>

        <div th:if="${success}" th:text="${success}" class="bg-green-50 text-green-700 px-4 py-2 rounded mb-4"></div>
        <div th:if="${error}" th:text="${error}" class="bg-red-50 text-red-700 px-4 py-2 rounded mb-4"></div>

        <div class="bg-white p-6 rounded-lg shadow-sm mb-6">
            <form th:action="@{/docs/upload}" method="post" enctype="multipart/form-data">
                <label class="block text-sm font-medium text-gray-700 mb-2">上传文档</label>
                <div class="flex gap-2">
                    <input type="file" name="file" required
                           accept=".pdf,.docx,.xlsx,.pptx,.txt,.md"
                           class="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100">
                    <button type="submit"
                            class="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 transition whitespace-nowrap">
                        上传
                    </button>
                </div>
                <p class="text-xs text-gray-500 mt-2">支持 PDF / Word / Excel / PPT / TXT / Markdown，上限 20MB</p>
            </form>
        </div>

        <div class="bg-white rounded-lg shadow-sm overflow-hidden">
            <table class="w-full">
                <thead class="bg-gray-50">
                    <tr>
                        <th class="text-left px-4 py-3 text-sm font-medium text-gray-600">文件名</th>
                        <th class="text-left px-4 py-3 text-sm font-medium text-gray-600">类型</th>
                        <th class="text-left px-4 py-3 text-sm font-medium text-gray-600">块数</th>
                        <th class="text-left px-4 py-3 text-sm font-medium text-gray-600">状态</th>
                        <th class="text-left px-4 py-3 text-sm font-medium text-gray-600">时间</th>
                        <th class="text-left px-4 py-3 text-sm font-medium text-gray-600">操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr th:each="doc : ${documents}" class="border-t">
                        <td class="px-4 py-3 text-sm" th:text="${doc.filename}"></td>
                        <td class="px-4 py-3">
                            <span class="text-xs bg-gray-100 px-2 py-1 rounded" th:text="${doc.fileType}"></span>
                        </td>
                        <td class="px-4 py-3 text-sm" th:text="${doc.chunkCount}"></td>
                        <td class="px-4 py-3">
                            <span th:classappend="${doc.status == 'READY'} ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'"
                                  class="text-xs px-2 py-1 rounded" th:text="${doc.status}"></span>
                        </td>
                        <td class="px-4 py-3 text-sm text-gray-500"
                            th:text="${#temporals.format(doc.createdAt, 'yyyy-MM-dd HH:mm')}"></td>
                        <td class="px-4 py-3">
                            <form th:action="@{/docs/{id}/delete(id=${doc.id})}" method="post"
                                  onsubmit="return confirm('确认删除？')">
                                <button class="text-red-500 text-sm hover:underline">删除</button>
                            </form>
                        </td>
                    </tr>
                    <tr th:if="${#lists.isEmpty(documents)}">
                        <td colspan="6" class="px-4 py-8 text-center text-gray-400">暂无文档，请上传</td>
                    </tr>
                </tbody>
            </table>
        </div>
    </main>
</body>
</html>
```

- [ ] **Step 4: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "feat: 添加文档上传和RAG ingestion流水线"
```

---

### Task 6: QA 问答模块

**Files:**
- Create: `src/main/java/com/zhiven/qa/service/QueryRouter.java`
- Create: `src/main/java/com/zhiven/qa/service/RAGService.java`
- Create: `src/main/java/com/zhiven/qa/controller/QAController.java`
- Create: `src/main/resources/templates/qa.html`

**Interfaces:**
- Consumes: `VectorStore` (Spring AI), `ChatClient.Builder` (Spring AI), `QaHistoryRepository` (Task 3), `UserService` (Task 4)
- Produces: `RAGService.answer(question, userId)` → `String answer` + sources

- [ ] **Step 1: 创建 QueryRouter.java**

```java
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
```

- [ ] **Step 2: 创建 RAGService.java**

```java
package com.zhiven.qa.service;

import com.zhiven.auth.entity.User;
import com.zhiven.qa.entity.QaHistory;
import com.zhiven.qa.repository.QaHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final QaHistoryRepository qaHistoryRepository;

    public RAGService(VectorStore vectorStore,
                      ChatClient.Builder chatClientBuilder,
                      QaHistoryRepository qaHistoryRepository) {
        this.vectorStore = vectorStore;
        this.chatClientBuilder = chatClientBuilder;
        this.qaHistoryRepository = qaHistoryRepository;
    }

    public record Answer(String content, List<String> sources) {}

    public Answer ask(String question, User user) {
        String route = QueryRouter.route(question);

        if ("SEARCH".equals(route)) {
            // 阶段 1 搜索走 RAG fallback（SearchService 预留）
            return ragAnswer(question, user);
        }
        return ragAnswer(question, user);
    }

    private Answer ragAnswer(String question, User user) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.defaults()
                        .withQuery(question)
                        .withTopK(5)
                        .withFilterExpression("user_id == " + user.getId())
        );

        if (docs.isEmpty()) {
            String fallback = "未找到与您问题相关的文档内容。请先上传相关文档。";
            saveHistory(user, question, fallback, "[]", "RAG");
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

        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        List<String> sources = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("filename", "unknown"))
                .distinct()
                .collect(Collectors.toList());

        saveHistory(user, question, answer, toSourcesJson(docs), "RAG");

        log.info("RAG 问答完成: {} → {} tokens", question, answer != null ? answer.length() : 0);
        return new Answer(answer, sources);
    }

    private void saveHistory(User user, String question, String answer, String sources, String route) {
        QaHistory history = new QaHistory(user, question, answer, sources, route);
        qaHistoryRepository.save(history);
    }

    private String toSourcesJson(List<Document> docs) {
        return docs.stream()
                .map(d -> String.format("{\"doc_id\":%s,\"chunk_index\":%s,\"content\":\"%s\"}",
                        d.getMetadata().getOrDefault("doc_id", ""),
                        d.getMetadata().getOrDefault("chunk_index", ""),
                        escapeJson(d.getContent() != null ? d.getContent().substring(0, Math.min(100, d.getContent().length())) : "")))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
```

- [ ] **Step 3: 创建 QAController.java**

```java
package com.zhiven.qa.controller;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import com.zhiven.qa.service.RAGService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/qa")
public class QAController {

    private final RAGService ragService;
    private final UserService userService;

    public QAController(RAGService ragService, UserService userService) {
        this.ragService = ragService;
        this.userService = userService;
    }

    @GetMapping
    public String qaPage() {
        return "qa";
    }

    @PostMapping("/ask")
    public String ask(@RequestParam String question,
                      Principal principal,
                      Model model) {
        User user = userService.findByUsername(principal.getName());
        RAGService.Answer answer = ragService.ask(question, user);
        model.addAttribute("question", question);
        model.addAttribute("answer", answer.content());
        model.addAttribute("sources", answer.sources());
        return "fragments/qa-result";
    }
}
```

- [ ] **Step 4: 创建 qa.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>智能问答 - 知问</title>
    <meta name="_csrf" th:content="${_csrf.token}"/>
    <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <div th:replace="~{layout :: navbar}"></div>

    <main class="max-w-4xl mx-auto px-4 py-8">
        <h1 class="text-xl font-bold text-gray-800 mb-6">智能问答</h1>

        <div class="bg-white p-6 rounded-lg shadow-sm mb-6">
            <form hx-post="/qa/ask"
                  hx-target="#qa-result"
                  hx-indicator="#qa-loading"
                  hx-headers='{"X-CSRF-TOKEN": document.querySelector("meta[name=_csrf]").content}'>
                <label class="block text-sm font-medium text-gray-700 mb-2">提出你的问题</label>
                <textarea name="question" rows="3" required
                          placeholder="例如：这份合同的核心条款是什么？第三章讲了什么？"
                          class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"></textarea>
                <div class="flex items-center gap-3 mt-2">
                    <button type="submit"
                            class="bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 transition">
                        提问
                    </button>
                    <div id="qa-loading" class="htmx-indicator text-gray-500 text-sm">
                        正在检索文档...
                    </div>
                </div>
            </form>
        </div>

        <div id="qa-result"></div>
    </main>
</body>
</html>
```

- [ ] **Step 5: 创建 qa-result 片段 `src/main/resources/templates/fragments/qa-result.html`**

```html
<div th:fragment="qa-result" class="bg-white rounded-lg shadow-sm p-6" th:if="${answer != null}">
    <div class="mb-4">
        <span class="text-xs text-gray-400 font-medium">问题</span>
        <p class="text-gray-800 mt-1" th:text="${question}"></p>
    </div>
    <div class="mb-4">
        <span class="text-xs text-gray-400 font-medium">回答</span>
        <div class="mt-2 text-gray-800 leading-relaxed whitespace-pre-wrap" th:text="${answer}"></div>
    </div>
    <div th:if="${!#lists.isEmpty(sources)}">
        <span class="text-xs text-gray-400 font-medium">引用文档</span>
        <div class="flex flex-wrap gap-1 mt-1">
            <span th:each="src : ${sources}"
                  class="text-xs bg-blue-50 text-blue-700 px-2 py-1 rounded"
                  th:text="${src}"></span>
        </div>
    </div>
</div>
```

- [ ] **Step 6: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add .
git commit -m "feat: 添加RAG智能问答模块"
```

---

### Task 7: Agent 模块

**Files:**
- Create: `src/main/java/com/zhiven/agent/tool/Tool.java`
- Create: `src/main/java/com/zhiven/agent/tool/ToolRegistry.java`
- Create: `src/main/java/com/zhiven/agent/tool/TextSummaryTool.java`
- Create: `src/main/java/com/zhiven/agent/tool/FormatConvertTool.java`
- Create: `src/main/java/com/zhiven/agent/service/AgentService.java`
- Create: `src/main/java/com/zhiven/agent/controller/AgentController.java`
- Create: `src/main/resources/templates/agent.html`
- Create: `src/main/resources/templates/fragments/agent-result.html`

**Interfaces:**
- Consumes: `ChatClient.Builder` (Spring AI), `AgentTaskRepository` (Task 3), `UserService` (Task 4)
- Produces: `AgentService.execute(request, User)` → AgentTask

- [ ] **Step 1: 创建 Tool.java 接口**

```java
package com.zhiven.agent.tool;

public interface Tool {
    String name();
    String description();
    String execute(String input);
}
```

- [ ] **Step 2: 创建 TextSummaryTool.java**

```java
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
```

- [ ] **Step 3: 创建 FormatConvertTool.java**

```java
package com.zhiven.agent.tool;

import org.springframework.stereotype.Component;

@Component
public class FormatConvertTool implements Tool {

    @Override
    public String name() {
        return "format_convert";
    }

    @Override
    public String description() {
        return "将文本转换为指定格式（邮件、报告、大纲等）";
    }

    @Override
    public String execute(String input) {
        // 阶段 1 简单实现：追加格式标记
        String[] parts = input.split("\\|", 2);
        String content = parts.length > 1 ? parts[1] : parts[0];
        String format = parts.length > 1 ? parts[0] : "报告";

        return "[" + format + "格式]\n" + content;
    }
}
```

- [ ] **Step 4: 创建 ToolRegistry.java**

```java
package com.zhiven.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.name(), tool);
        }
    }

    public Tool get(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new RuntimeException("未知工具: " + name);
        }
        return tool;
    }

    public List<String> listToolNames() {
        return tools.keySet().stream().sorted().toList();
    }

    public String describeTools() {
        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: 创建 AgentService.java**

```java
package com.zhiven.agent.service;

import com.zhiven.agent.entity.AgentTask;
import com.zhiven.agent.repository.AgentTaskRepository;
import com.zhiven.agent.tool.ToolRegistry;
import com.zhiven.auth.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient.Builder chatClientBuilder;
    private final ToolRegistry toolRegistry;
    private final AgentTaskRepository agentTaskRepository;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        ToolRegistry toolRegistry,
                        AgentTaskRepository agentTaskRepository) {
        this.chatClientBuilder = chatClientBuilder;
        this.toolRegistry = toolRegistry;
        this.agentTaskRepository = agentTaskRepository;
    }

    public AgentTask execute(String request, User user) {
        AgentTask task = new AgentTask(user, request);
        task.setStatus("RUNNING");
        agentTaskRepository.save(task);

        try {
            ChatClient client = chatClientBuilder.build();

            String planPrompt = """
                    你是一个智能任务助手。用户委托你完成以下任务。
                    可用工具：
                    %s

                    请分析任务并给出执行计划。如果任务需要调用工具，请按以下格式指定：
                    [TOOL:工具名]
                    输入内容
                    [/TOOL]

                    如果不需要工具，直接给出结果。

                    用户委托：%s
                    """.formatted(toolRegistry.describeTools(), request);

            String response = client.prompt().user(planPrompt).call().content();

            String result = processToolCalls(response);
            List<String> used = parseToolsUsed(response);

            task.setResult(result);
            task.setToolsUsed(used.toString());
            task.setStatus("DONE");
            log.info("Agent 任务完成: {}", request);
        } catch (Exception e) {
            task.setResult("任务执行失败: " + e.getMessage());
            task.setStatus("FAILED");
            log.error("Agent 任务失败", e);
        }

        return agentTaskRepository.save(task);
    }

    private String processToolCalls(String response) {
        StringBuilder result = new StringBuilder();
        int idx = 0;

        while (true) {
            int start = response.indexOf("[TOOL:", idx);
            if (start == -1) {
                result.append(response.substring(idx));
                break;
            }
            result.append(response, idx, start);

            int nameEnd = response.indexOf("]", start);
            String toolName = response.substring(start + 6, nameEnd);

            int contentStart = nameEnd + 1;
            int end = response.indexOf("[/TOOL]", contentStart);
            if (end == -1) break;

            String input = response.substring(contentStart, end).trim();
            String toolResult = toolRegistry.get(toolName).execute(input);
            result.append("\n[工具 ").append(toolName).append(" 结果]\n").append(toolResult);

            idx = end + 7;
        }

        return result.toString();
    }

    private List<String> parseToolsUsed(String response) {
        return toolRegistry.listToolNames().stream()
                .filter(response::contains)
                .toList();
    }
}
```

- [ ] **Step 6: 创建 AgentController.java**

```java
package com.zhiven.agent.controller;

import com.zhiven.agent.entity.AgentTask;
import com.zhiven.agent.service.AgentService;
import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;
    private final UserService userService;

    public AgentController(AgentService agentService, UserService userService) {
        this.agentService = agentService;
        this.userService = userService;
    }

    @GetMapping
    public String agentPage() {
        return "agent";
    }

    @PostMapping("/submit")
    public String submit(@RequestParam String task,
                         Principal principal,
                         Model model) {
        User user = userService.findByUsername(principal.getName());
        AgentTask result = agentService.execute(task, user);
        model.addAttribute("status", result.getStatus());
        model.addAttribute("result", result.getResult());
        return "fragments/agent-result";
    }
}
```

- [ ] **Step 7: 创建 agent.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>任务委托 - 知问</title>
    <meta name="_csrf" th:content="${_csrf.token}"/>
    <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <div th:replace="~{layout :: navbar}"></div>

    <main class="max-w-4xl mx-auto px-4 py-8">
        <h1 class="text-xl font-bold text-gray-800 mb-6">任务委托</h1>

        <div class="bg-white p-6 rounded-lg shadow-sm mb-6">
            <form hx-post="/agent/submit"
                  hx-target="#agent-result"
                  hx-indicator="#agent-loading"
                  hx-headers='{"X-CSRF-TOKEN": document.querySelector("meta[name=_csrf]").content}'>
                <label class="block text-sm font-medium text-gray-700 mb-2">描述你需要完成的任务</label>
                <textarea name="task" rows="4" required
                          placeholder="例如：把这份合同的关键条款总结成一封邮件  |  将以下内容翻译成英文  |  提取文档中的关键数据生成表格"
                          class="w-full px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"></textarea>
                <div class="flex items-center gap-3 mt-2">
                    <button type="submit"
                            class="bg-purple-600 text-white px-6 py-2 rounded hover:bg-purple-700 transition">
                        提交任务
                    </button>
                    <div id="agent-loading" class="htmx-indicator text-gray-500 text-sm">
                        正在执行任务...
                    </div>
                </div>
            </form>
        </div>

        <div id="agent-result"></div>
    </main>
</body>
</html>
```

- [ ] **Step 8: 创建 agent-result 片段**

```html
<div th:fragment="agent-result" class="bg-white rounded-lg shadow-sm p-6" th:if="${result != null}">
    <div class="flex items-center gap-2 mb-4">
        <span class="text-xs text-gray-400 font-medium">状态</span>
        <span th:classappend="${status == 'DONE'} ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
              class="text-xs px-2 py-1 rounded" th:text="${status}"></span>
    </div>
    <div class="mt-2 text-gray-800 leading-relaxed whitespace-pre-wrap" th:text="${result}"></div>
</div>
```

- [ ] **Step 9: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: 提交**

```bash
git add .
git commit -m "feat: 添加Agent任务委托模块和工具框架"
```

---

### Task 8: 搜索占位 + 历史记录

**Files:**
- Create: `src/main/java/com/zhiven/search/service/SearchService.java`
- Create: `src/main/java/com/zhiven/history/controller/HistoryController.java`
- Create: `src/main/resources/templates/history.html`

**Interfaces:**
- Consumes: `QaHistoryRepository` (Task 3), `UserService` (Task 4)
- Produces: SearchService 占位（阶段 2 实现）

- [ ] **Step 1: 创建 SearchService.java 占位**

```java
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
```

- [ ] **Step 2: 创建 HistoryController.java**

```java
package com.zhiven.history.controller;

import com.zhiven.auth.entity.User;
import com.zhiven.auth.service.UserService;
import com.zhiven.qa.entity.QaHistory;
import com.zhiven.qa.repository.QaHistoryRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

@Controller
public class HistoryController {

    private final QaHistoryRepository qaHistoryRepository;
    private final UserService userService;

    public HistoryController(QaHistoryRepository qaHistoryRepository, UserService userService) {
        this.qaHistoryRepository = qaHistoryRepository;
        this.userService = userService;
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        User user = userService.findByUsername(principal.getName());
        List<QaHistory> histories = qaHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        model.addAttribute("histories", histories);
        return "history";
    }
}
```

- [ ] **Step 3: 创建 history.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>问答历史 - 知问</title>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <div th:replace="~{layout :: navbar}"></div>

    <main class="max-w-4xl mx-auto px-4 py-8">
        <h1 class="text-xl font-bold text-gray-800 mb-6">问答历史</h1>

        <div class="space-y-4">
            <div th:each="h : ${histories}" class="bg-white rounded-lg shadow-sm p-4">
                <div class="flex items-center gap-2 mb-2">
                    <span class="text-xs bg-gray-100 px-2 py-0.5 rounded" th:text="${h.route}"></span>
                    <span class="text-xs text-gray-400"
                          th:text="${#temporals.format(h.createdAt, 'yyyy-MM-dd HH:mm')}"></span>
                </div>
                <p class="text-sm font-medium text-gray-700">Q: <span th:text="${h.question}"></span></p>
                <p class="text-sm text-gray-600 mt-2 line-clamp-3" th:text="${h.answer}"></p>
            </div>
            <div th:if="${#lists.isEmpty(histories)}" class="text-center text-gray-400 py-12">
                暂无问答记录
            </div>
        </div>
    </main>
</body>
</html>
```

- [ ] **Step 4: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "feat: 添加问答历史和搜索占位服务"
```

---

### Task 9: 首页 + 导航 + 样式

**Files:**
- Create: `src/main/resources/templates/layout.html`
- Create: `src/main/resources/templates/index.html`
- Create: `src/main/resources/static/css/style.css`

**Interfaces:**
- Consumes: 无（仅消费 Spring Security context）
- Produces: 全局布局导航 + 首页

- [ ] **Step 1: 创建 layout.html （导航栏）**

```html
<nav th:fragment="navbar" class="bg-white shadow-sm border-b"
     xmlns:th="http://www.thymeleaf.org"
     xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
    <div class="max-w-4xl mx-auto px-4 py-3 flex items-center justify-between">
        <a href="/" class="text-lg font-bold text-blue-700">知问</a>
        <div class="flex items-center gap-4">
            <a href="/docs" class="text-sm text-gray-600 hover:text-blue-600 transition">文档</a>
            <a href="/qa" class="text-sm text-gray-600 hover:text-blue-600 transition">问答</a>
            <a href="/agent" class="text-sm text-gray-600 hover:text-blue-600 transition">任务</a>
            <a href="/history" class="text-sm text-gray-600 hover:text-blue-600 transition">历史</a>
            <span class="text-sm text-gray-400" sec:authentication="name"></span>
            <form th:action="@{/logout}" method="post" class="inline">
                <button class="text-sm text-gray-500 hover:text-red-600 transition">退出</button>
            </form>
        </div>
    </div>
</nav>
```

- [ ] **Step 2: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>知问 文档智能体</title>
    <script src="https://cdn.tailwindcss.com"></script>
</head>
<body class="bg-gray-50 min-h-screen">
    <div th:replace="~{layout :: navbar}"></div>

    <main class="max-w-4xl mx-auto px-4 py-16 text-center">
        <h1 class="text-3xl font-bold text-gray-800 mb-4">知问 文档智能体</h1>
        <p class="text-gray-500 mb-12">上传文档，用自然语言精准问答、编辑、生成</p>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <a href="/docs" class="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition">
                <div class="text-blue-600 text-2xl mb-3">+</div>
                <h2 class="font-semibold text-gray-800 mb-2">文档管理</h2>
                <p class="text-sm text-gray-500">上传 PDF、Word、Excel 等文档</p>
            </a>
            <a href="/qa" class="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition">
                <div class="text-green-600 text-2xl mb-3">?</div>
                <h2 class="font-semibold text-gray-800 mb-2">智能问答</h2>
                <p class="text-sm text-gray-500">基于文档内容精准问答</p>
            </a>
            <a href="/agent" class="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition">
                <div class="text-purple-600 text-2xl mb-3">~</div>
                <h2 class="font-semibold text-gray-800 mb-2">任务委托</h2>
                <p class="text-sm text-gray-500">AI 自主调用工具完成复杂任务</p>
            </a>
        </div>
    </main>
</body>
</html>
```

- [ ] **Step 3: 创建 style.css**

```css
.htmx-indicator {
    opacity: 0;
    transition: opacity 200ms ease-in;
}
.htmx-request .htmx-indicator {
    opacity: 1;
}
.htmx-request.htmx-indicator {
    opacity: 1;
}
```

- [ ] **Step 4: 构建验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "feat: 添加首页布局和导航"
```

---

### Task 10: 集成验证与最终修复

此任务为验证性任务，无新文件创建。检查所有模块的集成是否正确。

- [ ] **Step 1: 补充 layout.html 中的完整 HTML 结构**

当前每个页面用自己的 `<html>` 标签引入 Tailwind 和 htmx CDN，确认 `layout.html` 和各个页面之间没有冲突。确保 `layout.html` 的 `<head>` 部分包含 Thymeleaf `sec` 命名空间。由于页面各自引入 CDN，layout 无需提供 `<head>` 内容——各页面自行管理。

> 注：如需统一管理 CDN，可在 layout.html 中添加 `head` fragment。

- [ ] **Step 2: 确认 SecurityConfig 放行静态资源**

检查 `SecurityConfig` 中已包含 `.requestMatchers("/css/**").permitAll()`（Task 2 已完成）。

- [ ] **Step 3: 运行完整编译**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 启动应用检查（需要本地 PostgreSQL）**

Run: `mvn spring-boot:run`
Expected: 应用启动成功，连接 PostgreSQL 成功

- [ ] **Step 5: 提交**

```bash
git add .
git commit -m "chore: 集成验证与最终修复"
```

---

## 自检清单

1. **Spec coverage**:
   - 用户认证模块 — Task 4 ✓
   - 文档上传/Ingestion — Task 5 ✓
   - RAG 问答 — Task 6 ✓
   - Agent 工具调用 — Task 7 ✓
   - 搜索占位 — Task 8 ✓
   - 历史记录 — Task 8 ✓
   - 安全配置 — Task 2 ✓

2. **Placeholder check**: 无 TBD/TODO，SearchService 作为清晰的占位类。

3. **Type consistency**:
   - `User` 由 Task 3 定义，Task 4-8 消费 ✓
   - `UserService.findByUsername()` 返回 `User` — 所有 Controller 使用一致 ✓
   - `VectorStore` 由 Spring AI 自动配置，无需 Task 内定义 ✓
   - `ChatClient.Builder` 由 Spring AI 自动注入 ✓
