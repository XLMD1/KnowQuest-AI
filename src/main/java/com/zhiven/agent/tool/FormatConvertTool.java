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
