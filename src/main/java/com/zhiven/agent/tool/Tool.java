package com.zhiven.agent.tool;

public interface Tool {
    String name();
    String description();
    String execute(String input);
}
