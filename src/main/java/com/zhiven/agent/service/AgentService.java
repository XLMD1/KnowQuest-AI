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

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final AgentTaskRepository agentTaskRepository;

    public AgentService(ChatClient.Builder chatClientBuilder,
                        ToolRegistry toolRegistry,
                        AgentTaskRepository agentTaskRepository) {
        this.chatClient = chatClientBuilder.build();
        this.toolRegistry = toolRegistry;
        this.agentTaskRepository = agentTaskRepository;
    }

    public AgentTask execute(String request, User user) {
        AgentTask task = new AgentTask(user, request);
        agentTaskRepository.save(task);

        try {
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

            String response = chatClient.prompt().user(planPrompt).call().content();

            var toolResult = processToolCalls(response);
            String result = toolResult.result;
            List<String> used = toolResult.toolsUsed;

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

    private record ToolCallResult(String result, List<String> toolsUsed) {}

    private ToolCallResult processToolCalls(String response) {
        StringBuilder result = new StringBuilder();
        List<String> toolsUsed = new java.util.ArrayList<>();
        int idx = 0;

        while (true) {
            int start = response.indexOf("[TOOL:", idx);
            if (start == -1) {
                result.append(response.substring(idx));
                break;
            }
            result.append(response, idx, start);

            int nameEnd = response.indexOf("]", start);
            String toolName = response.substring(start + 6, nameEnd).trim();

            int contentStart = nameEnd + 1;
            int end = response.indexOf("[/TOOL]", contentStart);
            if (end == -1) break;

            String input = response.substring(contentStart, end).trim();
            String toolResult = toolRegistry.get(toolName).execute(input);
            result.append("\n[工具 ").append(toolName).append(" 结果]\n").append(toolResult);
            toolsUsed.add(toolName);

            idx = end + 7;
        }

        return new ToolCallResult(result.toString(), toolsUsed);
    }
}
