package com.spzx.report.vectorTools;

import com.spzx.report.aiTools.AiConst;
import com.spzx.report.aiTools.EmbeddingConst;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

public class SpzxEmbeddingTools {

    private static EmbeddingModel model;
    static {
        model = OpenAiEmbeddingModel.builder()
                .apiKey(AiConst.Api_Key)
                .modelName(EmbeddingConst.Embedding_Model)
                .baseUrl(EmbeddingConst.Base_Url_Embedding)
                .dimensions(1536)
                .build();
    }

    public static Embedding getEmbedding(String cleanText) {
        // 像文本向量化
        return model.embed(cleanText).content();
    }
}
