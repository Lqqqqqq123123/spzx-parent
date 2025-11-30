package com.spzx.report.service.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.report.aiTools.SpzxAiQuest;
import com.spzx.report.domain.VOrderInfo;
import com.spzx.report.domain.VOrderInfoJSONObject;
import com.spzx.report.mapper.VOrderInfoMapper;
import com.spzx.report.service.ISpzxReportService;
import com.spzx.report.vectorTools.PineConeConst;
import com.spzx.report.vectorTools.PineconeSimilaryUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SpzxReportServiceImpl extends ServiceImpl<VOrderInfoMapper, VOrderInfo> implements ISpzxReportService{

    @Override
    public List<Map<String, Object>> getAiReport(String question) {
        // 1. 调用大模型去过滤关键字
        SpzxAiQuest spzxAiQuest = new SpzxAiQuest();
        String jsonResp = spzxAiQuest.processQuestion(question);



        // 2. 将 json 转换为  VOrderInfoJSONObject
        VOrderInfoJSONObject vOrderInfoJSONObject = JSON.parseObject(jsonResp, VOrderInfoJSONObject.class);
        System.out.println("vOrderInfoJSONObject = " + vOrderInfoJSONObject);


        // 3. 用向量数据库匹配相似工具，匹配ai过滤关键字,获取元数据(column和word),作为后期拼接报表sql的具体字段和条件数据

        VOrderInfoJSONObject vectorData = getVectorData(vOrderInfoJSONObject);

        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper = getMyReportWrapper(queryWrapper,vectorData);

        List<Map<String, Object>> maps = getBaseMapper().selectMaps(queryWrapper);

        return maps;
    }

    /***
     * 1 向量数据库匹配相似ai过滤关键词
     * @param vOrderInfoJSONObject
     * @return
     */
    private VOrderInfoJSONObject getVectorData(VOrderInfoJSONObject vOrderInfoJSONObject) {
        // 1 分组和聚合关键字处理
        // 此处通过向量数据库，关联用户的信息和本系统的数据字段
        String countKeyword = vOrderInfoJSONObject.getCountKeyword();
        String groupKeyword = vOrderInfoJSONObject.getGroupKeyword();

        // String countKeywordSimilarityEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(countKeyword, PineConeConst.PCK_INDEX_COUNT_KEY_1).get("column");
        String countKeywordSimilarityEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(StringUtils.isEmpty(countKeyword)?"null":countKeyword, PineConeConst.PCK_INDEX_COUNT_KEY_1).get("column");
        String groupKeywordSimilarityEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(StringUtils.isEmpty(groupKeyword)?"null":groupKeyword, PineConeConst.PGK_INDEX_GROUP_KEY_1).get("column");

        // 处理向量相似匹配结果
        // 将向量比对后的结果更新到json对象中
        // 如果用户没有提及与分组相关信息，则默认以最近一周的时间作为分组
        if(StringUtils.isEmpty(groupKeywordSimilarityEmbeddings)){
            groupKeywordSimilarityEmbeddings = "create_date";
        }
        // 如果用户没有提及与统计聚合相关信息，则默认以订单个数为统计聚合标准
        if(StringUtils.isEmpty(countKeywordSimilarityEmbeddings)){
            groupKeywordSimilarityEmbeddings = "order_id";
        }
        vOrderInfoJSONObject.setGroupKeyword(groupKeywordSimilarityEmbeddings.trim());// 分组关键字
        vOrderInfoJSONObject.setCountKeyword(countKeywordSimilarityEmbeddings.trim());// 聚合关键字

        // 2 条件过滤关键字处理，用户的分类关键字，因为系统分类有三级，所以这里需要确定使用哪一个分类，这里默认任何级别都可以，看具体需求
        //String categoryKeyword = vOrderInfoJSONObject.getCategoryKeyword();
        //String category3Name = vOrderInfoJSONObject.getCategory3Name();
        String provinceName = vOrderInfoJSONObject.getProvinceName();
        String tmName = vOrderInfoJSONObject.getTmName();
        String skuName = vOrderInfoJSONObject.getSkuName();

        // 分类可能匹配多个,这部分代实际工作中，可以以后单独处理，现在为了实现功能，暂且不用加入(伪代码)
//        if(StringUtils.hasText(categoryKeyword)){
//            List<Map<String, String>> categoryListMap = PineconeSimilaryUtil.getCategoryPineconeSimilarityEmbeddings(categoryKeyword, PineconeConst.PSK_INDEX_SELECT_KEY_1);
//            vOrderInfoJSONObject.setCategory3ListMap(categoryListMap);// 分类条件关键字
//        }


        if(StringUtils.hasText(provinceName)){
            String provinceNameEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(provinceName, PineConeConst.PSK_INDEX_SELECT_KEY_1).get("word");
            vOrderInfoJSONObject.setProvinceName(provinceNameEmbeddings.trim());// 地区条件关键字
        }


        if(StringUtils.hasText(tmName)){
            String tmNameEmbeddings =PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(tmName,PineConeConst.PSK_INDEX_SELECT_KEY_1).get("word");
            vOrderInfoJSONObject.setTmName(tmNameEmbeddings.trim());// 商标条件关键字
        }


        if(StringUtils.hasText(skuName)){
            String skuNameEmbeddings = PineconeSimilaryUtil.getPineconeSimilarityEmbeddings(skuName,PineConeConst.PSK_INDEX_SELECT_KEY_1).get("word");
            vOrderInfoJSONObject.setSkuName(skuNameEmbeddings.trim());// 商品条件关键字
        }

        return vOrderInfoJSONObject;
    }


    /***
     * 2 拼接sql语句
     * @param vOrderInfoQueryWrapper
     * @param vectorData
     * @return
     */
    private QueryWrapper<VOrderInfo> getMyReportWrapper(QueryWrapper<VOrderInfo> vOrderInfoQueryWrapper , VOrderInfoJSONObject vectorData) {

        // 聚合统计字段处理
        // 人数user_id or 个数order_id or 总金额order_amount
        String selectStr = null;
        if (vectorData.getCountKeyword().equals("order_amount")) {
            selectStr = "sum(" + vectorData.getCountKeyword() + ") as count";
        } else {
            selectStr = "count(DISTINCT " + vectorData.getCountKeyword() + ") as count";
        }

        // 分组字段处理
        if(vectorData.getGroupKeyword().equals("create_date")){
            vOrderInfoQueryWrapper
                    .select("DATE_FORMAT("+vectorData.getGroupKeyword()+",'%Y-%m-%d') as groupTag", selectStr)
                    .groupBy("DATE_FORMAT("+vectorData.getGroupKeyword()+",'%Y-%m-%d')");
        }else{
            vOrderInfoQueryWrapper
                    .select(vectorData.getGroupKeyword() + " as groupTag", selectStr)
                    .groupBy(vectorData.getGroupKeyword());
        }

        // 条件字段处理
        String skuName = vectorData.getSkuName();
        String provinceName = vectorData.getProvinceName();
        String tmName = vectorData.getTmName();
        //List<Map<String, String>> category3ListMap = vectorData.getCategory3ListMap();

        vOrderInfoQueryWrapper.eq(StringUtils.hasText(skuName),"sku_name",skuName);
        vOrderInfoQueryWrapper.eq(StringUtils.hasText(provinceName),"province_name",provinceName);
        vOrderInfoQueryWrapper.eq(StringUtils.hasText(tmName),"tm_name",tmName);

        return vOrderInfoQueryWrapper;
    }
}
