package com.zhiven.agent.tool;

import org.springframework.stereotype.Component;

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
