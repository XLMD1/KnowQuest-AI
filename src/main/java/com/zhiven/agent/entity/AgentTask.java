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

    @Column(name = "tools_used", columnDefinition = "TEXT")
    private String toolsUsed;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AgentTask() {}

    public AgentTask(User user, String request) {
        this.user = user;
        this.request = request;
        this.status = "RUNNING";
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
