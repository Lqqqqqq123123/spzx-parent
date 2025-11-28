package com.spzx.report.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class VOrderInfo {
    private Long orderId;
    private String orderNo;
    private BigDecimal orderAmount;
    private Integer skuNum;// 商品数量
    private String skuName;// 商品名
    private Long userId;
    private Long gender; // 性别id
    private String genderName;// 性别名
    private Long provinceId;// 省id
    private String provinceName;// 省名
    private Long category1Id;// 一级分类id
    private String category1Name;// 一级分类名
    private Long category2Id;// 二级分类id
    private String category2Name;// 二级分类名
    private Long category3Id;// 三级分类id
    private String category3Name;// 三级分类名
    private Long tmId;// 品牌id
    private String tmName;// 品牌名
    private Long orderStatus;// 订单状态
    private Date createDate;// 创建日期

    @TableField(exist = false)
    private String categoryKeyword; // 用户输入的分类关键字

}
