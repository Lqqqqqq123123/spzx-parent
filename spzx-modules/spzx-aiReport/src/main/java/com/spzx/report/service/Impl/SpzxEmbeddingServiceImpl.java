package com.spzx.report.service.Impl;

import com.spzx.report.mapper.VectorCountKeyMapper;
import com.spzx.report.mapper.VectorGroupKeyMapper;
import com.spzx.report.mapper.VectorSelectKeyMapper;
import com.spzx.report.service.SpzxEmbeddingService;
import com.spzx.report.vectorTools.PineConeConst;
import com.spzx.report.vectorTools.PineconeUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SpzxEmbeddingServiceImpl implements SpzxEmbeddingService {

    @Autowired
    private VectorCountKeyMapper vectorCountKeyMapper;
    @Autowired
    private VectorGroupKeyMapper vectorGroupKeyMapper;
    @Autowired
    private VectorSelectKeyMapper vectorSelectKeyMapper;
    @Override
    public void embeddingGroupKeyToPinecone() {
        // 查出来
        List<Map<String, Object>> list = vectorGroupKeyMapper.selectMaps(null);

//        for (Map<String, Object> obj : list) {
//            obj.forEach((key, value) -> {
//                System.out.println(key + ":" + value);
//            });
//
//            System.out.println("===========================================================================");
//        }

        // 直接存到向量数据库中
        PineconeUploadUtil.uploadListToPinecone(list, PineConeConst.PGK_INDEX_UPSERT_URL, PineConeConst.Api_Key);
    }

    @Override
    public void embeddingCountKeyToPinecone() {
        List<Map<String, Object>> list = vectorCountKeyMapper.selectMaps(null);
        PineconeUploadUtil.uploadListToPinecone(list, PineConeConst.PCK_INDEX_UPSERT_URL, PineConeConst.Api_Key);

    }

    @Override
    public void embeddingSelectKeyToPinecone() {
        List<Map<String, Object>> list = vectorSelectKeyMapper.selectMaps(null);
        PineconeUploadUtil.uploadListToPinecone(list, PineConeConst.PSK_INDEX_UPSERT_URL, PineConeConst.Api_Key);
    }
}
