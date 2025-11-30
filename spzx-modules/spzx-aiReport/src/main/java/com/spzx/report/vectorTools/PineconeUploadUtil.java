package com.spzx.report.vectorTools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spzx.report.vectorTools.SpzxEmbeddingTools;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PineconeUploadUtil {

    static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /***
     * 通过集合上传，前期数据处理
     * @param list
     */
    public static void uploadListToPinecone(List<Map<String, Object>> list, String pineconeIndexKey, String pineconeKey) {
        List<Map<String, Object>> batch = new ArrayList<>();// 分批
        int lineNum = 0; // 记录行号
        int successCount = 0; // 成功上传的数量
        int failedCount = 0; // 失败的数量

        for (Map<String, Object> map : list) {
            String cleanText = map.get("key_word").toString().trim();

            // 生成文本向量
            float[] vector = SpzxEmbeddingTools.getEmbedding(cleanText).vector();
            if (vector.length == 0) {
                System.err.println(" 向量化失败，跳过行 " + lineNum + ": " + cleanText);
                failedCount++;
                continue;
            }

            // 构造 Pinecone 记录
            Map<String, Object> vectorEntry = new HashMap<>();
            vectorEntry.put("id", "no_" + lineNum);
            vectorEntry.put("values", vector);

            // 添加 metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("column", map.get("key_column").toString().trim());
            metadata.put("word", map.get("key_word").toString().trim());
            vectorEntry.put("metadata", metadata);
            batch.add(vectorEntry);
            // System.out.println(batch);
            lineNum++;

            // 批量上传
            if (batch.size() >= 50) {// 批处理，每次上传50条
                upsertToPinecone(batch,pineconeIndexKey,pineconeKey);
                successCount += batch.size();
                batch.clear();
            }
        }

        // 处理剩余数据
        if (!batch.isEmpty()) {
            upsertToPinecone(batch,pineconeIndexKey,pineconeKey);
            successCount += batch.size();
        }

        System.out.println("上传完成，共处理 " + lineNum + " 行");
        System.out.println("成功上传：" + successCount);
        System.out.println("失败上传：" + failedCount);
    }



    // 将数据上传至向量库
    private static void upsertToPinecone(List<Map<String, Object>> vectors,String pineconeIndexKey,String pineconeKey) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(pineconeIndexKey);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Api-Key", pineconeKey);
            // 构造 JSON 请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vectors", vectors);
            requestBody.put("namespace","default");
            String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);
            // 设置 UTF-8 编码
            //请注意！！！这里务必进行编码的设置，否则其默认不会将jsonBody中的中文数据成功发送，会乱码
            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            // 发送请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseString = EntityUtils.toString(response.getEntity());
                System.out.println("Pinecone upsert response: " + responseString);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}