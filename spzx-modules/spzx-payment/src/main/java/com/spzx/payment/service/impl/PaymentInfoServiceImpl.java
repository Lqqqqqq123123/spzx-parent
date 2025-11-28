package com.spzx.payment.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.common.security.utils.SecurityUtils;
import com.spzx.order.api.RemoteOrderInfoService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.mapper.PaymentInfoMapper;
import com.spzx.payment.service.IPaymentInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 付款信息Service业务层处理
 */
@Slf4j
@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements IPaymentInfoService {
    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RemoteOrderInfoService remoteOrderInfoService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private AlipayClient alipayClient;


    @Override
    public PaymentInfo  savePaymentInfo(String orderNo) {
        // 1. 先去判重
        LambdaQueryWrapper<PaymentInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentInfo::getOrderNo, orderNo);
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(queryWrapper);

        if(paymentInfo != null){
            // 1.1 之前以及保存成功了
            return paymentInfo;
        }

        paymentInfo = new PaymentInfo();

        // 2.1 封装数据

        // 查询该订单详细数据
        R<OrderInfo> byOrderNo = remoteOrderInfoService.getByOrderNo(orderNo, SecurityConstants.INNER);

        // 降级处理
        if(!R.isSuccess(byOrderNo)){
            throw new RuntimeException("远程服务：getByOrderNo 失败");
        }
        OrderInfo orderInfo = byOrderNo.getData();
//        paymentInfo.setUserId(SecurityUtils.getUserId());
//        paymentInfo.setOrderNo(orderNo);
//        paymentInfo.setPayType(2); // 支付宝付款 // todo 理论上是前端传
//        paymentInfo.setPaymentStatus("0"); // 待付款
//        paymentInfo.setAmount(orderInfo.getTotalAmount()); //todo 支付金额修改，调正式的记得改成0.01
//        paymentInfo.setCreateTime(new Date());
//        List<String> contents = orderInfo.getOrderItemList().stream()
//                                .map(item -> item.getSkuName() + ":" + item.getSkuNum())
//                                .toList();
//        log.info("contents: {}", contents);
//        paymentInfo.setContent(String.join("|", contents));


        paymentInfo.setUserId(orderInfo.getUserId());
        String content = "";
        for(OrderItem item : orderInfo.getOrderItemList()) {
            content += item.getSkuName() + " ";
        }
        paymentInfo.setContent(content);
        paymentInfo.setAmount(orderInfo.getTotalAmount());
        paymentInfo.setOrderNo(orderNo);
        paymentInfo.setPaymentStatus("0");

        // 2.2 保存数据
        paymentInfoMapper.insert(paymentInfo); // 主键回显
        return paymentInfo;

    }

    @Override
    public void customUpdatePaymentInfo(Map<String, String> paramMap, int pay_type) throws ParseException {


        // 去重处理
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo, paramMap.get("out_trade_no")));

        if(paymentInfo.getPaymentStatus().equals("1")){
            return;
        }

        LambdaUpdateWrapper<PaymentInfo> updateWrapper = new LambdaUpdateWrapper<>();
        String tradeNo = paramMap.get("trade_no");
        Date date = new Date();
        updateWrapper.set(PaymentInfo::getTradeNo, tradeNo);
        updateWrapper.set(PaymentInfo::getCallbackTime, date);
        updateWrapper.set(PaymentInfo::getPaymentStatus, "1");
        updateWrapper.set(PaymentInfo::getCallbackContent, JSON.toJSONString(paramMap));
        updateWrapper.set(PaymentInfo::getPaymentStatus, String.valueOf(pay_type));

        // 更新支付信息
        update(updateWrapper);



    }
}