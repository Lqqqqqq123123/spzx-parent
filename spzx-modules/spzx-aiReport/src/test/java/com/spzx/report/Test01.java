package com.spzx.report;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class Test01 {

    @Test
    public void test01(){
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey("sk-79e3a8f07e954bbfa03f5ddcbbd8f9da")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .modelName("deepseek-v3")
                .build();

        String ans = model.chat("如果你是人类，你想做什么");
        System.out.println(ans);

    }
}
