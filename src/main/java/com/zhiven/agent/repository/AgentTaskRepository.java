package com.zhiven.agent.repository;

import com.zhiven.agent.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
    List<AgentTask> findByUserIdOrderByCreatedAtDesc(Long userId);
}
