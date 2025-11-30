package com.spzx.report;

import com.spzx.report.aiTools.AiConst;
import com.spzx.report.aiTools.EmbeddingConst;
import com.spzx.report.service.SpzxEmbeddingService;
import com.spzx.report.vectorTools.PineConeConst;
import com.spzx.report.aiTools.SpzxAiQuest;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

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



    @Test
    public void test02(){
        SpzxAiQuest quest = new SpzxAiQuest();
        quest.processQuestion("上海女性用户美妆品类最近三个月xx粉底液的最热门的各种品牌的信息");
    }

    @Test
    public void test03(){
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(AiConst.Api_Key)
                .modelName(EmbeddingConst.Embedding_Model)
                .baseUrl(EmbeddingConst.Base_Url_Embedding)
                .dimensions(1536)
                .build();

        Response<Embedding> resp = model.embed("我是刘强");

        System.out.println(resp.content().dimension());
    }


    @Test
    public void test04(){
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey(AiConst.Api_Key)
                .modelName(EmbeddingConst.Embedding_Model)
                .baseUrl(EmbeddingConst.Base_Url_Embedding)
                .dimensions(1536)
                .build();


        // 构建segment
        EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
                        .apiKey(PineConeConst.Api_Key)
                        .index("spzx-report-select-key")
                        .createIndex(
                                PineconeServerlessIndexConfig.builder()
                                        .cloud("aws")
                                        .region("us-east-1")
                                        .dimension(1536)
                                        .build()
                        )
                        .build();

        TextSegment meta = TextSegment.from("大漠孤烟直，长河落日圆",
                Metadata.metadata("作者" , "ltb").put("时间" , new Date().toString()));

        Embedding embedding = model.embed(meta).content();

        embeddingStore.add(embedding,meta);

    }

    @Autowired
    private SpzxEmbeddingService spzxEmbeddingService;

    @Test
    public void testAddToPinecone(){
        spzxEmbeddingService.embeddingCountKeyToPinecone();
        spzxEmbeddingService.embeddingSelectKeyToPinecone();
    }
}

