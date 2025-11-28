package com.spzx.report.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class VOrderInfoJSONObject extends VOrderInfo{

    private String groupKeyword; // 用户想要的分组内容(x轴)

    private String countKeyword; // 用户想要的聚合内容（y轴）

    private List<Map<String,String>> category3ListMap;

}
