package com.spzx.report.aiTools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

public class SpzxAiQuest {
    // 初始化组件
    private static ChatModel deepseek;
    private static ChatModel qwen;
    private static MyAssistAgent agent;

     public SpzxAiQuest(){
        deepseek = OpenAiChatModel.builder()
                .apiKey(AiConst.Api_Key)
                .baseUrl(AiConst.Base_Url_DeepSeek)
                .modelName(AiConst.DeepSeek)
                .build();

        qwen = OpenAiChatModel.builder()
                .apiKey(AiConst.Api_Key)
                .modelName(AiConst.Qwen)
                .baseUrl(AiConst.Base_Url_Qwen)
                .build();

        agent = AiServices.builder(MyAssistAgent.class).
                chatModel(qwen)
                .systemMessageProvider((t) -> {
                    return PromptGenerator.prompt;
                })
                .build();
    }

    /***
     * 控制层调用过程
     * @param question
     * @return
     * @throws Exception
     */
    public String processQuestion(String question) {

        System.out.println("1 原始问题："+question);

        //组合提示词模板
        System.out.println("2 组合提示词模板=============================================");
        String questionMerge = PromptGenerator.question(question);
        System.out.println(questionMerge);

        // 通义千问
        System.out.println("3 提问：通义千问===============================================");
        String answer = agent.chat(questionMerge);
        int jsonStart = answer.lastIndexOf("{");
        int jsonEnd = answer.lastIndexOf("}");
        System.out.println("4 回答：通义千问===============================================");
        System.out.println(answer);
        String jsonAnswer = answer.substring(jsonStart, jsonEnd+1);
        return  jsonAnswer;
    }

}
