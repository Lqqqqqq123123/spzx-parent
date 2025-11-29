package com.spzx.report.aiTools;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;

public class PromptGenerator {
    // 简单的提示词可以直接使用字符串
    public static final String Prompt_Template = "";


    // 文本提示词模板
    public static final String prompt = "你是一个精通电商领域的助手，需要根据用户的要求准确回答问题。" + PromptGenerator.readPromptFile();// 系统提示词信息

    private static String readPromptFile() {
        Resource resource = new ClassPathResource("aiReportPrompt.txt");

        String template = null;
        try {
            template = FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream()));
            return template;
        } catch (IOException e) {
            System.out.println("读取提示词文件失败");
        }
        return  "";
    }


    public static String question(String question) {
        LocalDateTime now = LocalDateTime.now();
        return "【当前问题】" + question + "\n【当前时间】" + now;
    }
}
