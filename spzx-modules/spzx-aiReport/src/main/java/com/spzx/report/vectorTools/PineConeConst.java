package com.spzx.report.vectorTools;

import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;

public class PineConeConst {


    //pinecone-key
    public static final String Api_Key = "pcsk_7qPfn_BchUaHLdp3CuJMD2KAUQhMSkhHKHZ2aiMbttaeR8RjiNfYCFovzWGR5LvkHcQus";

    // pinecone-db group-key 上传upsert
    public static final String PGK_INDEX_UPSERT_URL = "https://spzx-report-group-key-68y21kl.svc.aped-4627-b74a.pinecone.io/vectors/upsert";
    // pinecone-db count-key 上传upsert
    public static final String PCK_INDEX_UPSERT_URL = "https://spzx-report-count-key-68y21kl.svc.aped-4627-b74a.pinecone.io/vectors/upsert";
    // pinecone-db select-key 上传upsert
    public static final String PSK_INDEX_UPSERT_URL = "https://spzx-report-select-key-68y21kl.svc.aped-4627-b74a.pinecone.io/vectors/upsert";

    // pinecone-db group-key 查询query
    public static final String PGK_INDEX_QUERY_URL = "https://spzx-report-group-key-68y21kl.svc.aped-4627-b74a.pinecone.io/query";

    // pinecone-db count-key 查询query
    public static final String PCK_INDEX_QUERY_URL = "https://spzx-report-count-key-68y21kl.svc.aped-4627-b74a.pinecone.io/query";

    // pinecone-db select-key 查询query
    public static final String PSK_INDEX_QUERY_URL = "https://spzx-report-select-key-68y21kl.svc.aped-4627-b74a.pinecone.io/query";

    public static final String PGK_INDEX_GROUP_KEY_1 = "spzx-report-group-key";
    public static final String PCK_INDEX_COUNT_KEY_1 = "spzx-report-count-key";
    public static final String PSK_INDEX_SELECT_KEY_1 = "spzx-report-select-key";
}
