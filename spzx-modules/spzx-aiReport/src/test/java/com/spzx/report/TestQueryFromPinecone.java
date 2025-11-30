package com.spzx.report;

import com.spzx.report.vectorTools.PineConeConst;
import com.spzx.report.vectorTools.SpzxEmbeddingTools;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;

import java.util.List;

@SpringBootTest
public class TestQueryFromPinecone {
    public static void main(String[] args) {
        PineconeEmbeddingStore store =  PineconeEmbeddingStore.builder()
                .apiKey(PineConeConst.Api_Key)
                .index(PineConeConst.PGK_INDEX_GROUP_KEY_1)
                .metadataTextKey("column")
                .metadataTextKey("word")
                .nameSpace("default")
                .build();

        String queryText = "女性最爱买的商品";

        EmbeddingSearchRequest query = EmbeddingSearchRequest.builder()
                .maxResults(3) // 最多返回多少个结果
                //.minScore() 最小分数
                .queryEmbedding(SpzxEmbeddingTools.getEmbedding(queryText))
                .build();

        List<EmbeddingMatch<TextSegment>> list = store.search(query).matches();

        if(CollectionUtils.isEmpty(list)){
            System.out.println("没有查询到结果");
        } else{
            list.forEach(t -> {
                System.out.println("id:"+t.embeddingId());
                System.out.println("分数:"+t.score());
                System.out.println("内容:"+t.embedded().text());
                System.out.println("元数据:"+t.embedded().metadata());
            });
        }

    }
}
