package com.spzx.payment.service.impl;

import ch.qos.logback.core.util.EnvUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.env.EnvironmentUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class AlipayServiceImpl implements IAlipayService {

    @Autowired
    AlipayClient alipayClient;

    @Autowired
    IPaymentInfoService paymentInfoService;


    // 1. 先去保存支付信息

    // 2. 调用支付宝接口

    // 3. 根据支付宝返回的结果更新支付信息

    // 3.1 支付成功：修改订单状态

    // 3.2 支付成功：去减库存

    // 3.3 支付失败：直接关闭订单，然后去解锁库存


    @Override
    @SneakyThrows
    public String submitAlipay(String orderNo)
    {
        // 1. 保存支付信息
        PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(orderNo);

        // 2 调用支付宝 api
        // 2.1 新建一个请求
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();

        // 同步回调
        // return_payment_url=http://sph-payment.atguigu.cn/alipay/callback/return
        request.setReturnUrl(AlipayConfig.return_payment_url);
        // 异步回调
        request.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址
        // 参数
        // 声明一个map 集合
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",paymentInfo.getOrderNo());
        map.put("product_code","QUICK_WAP_WAY");
        map.put("total_amount",paymentInfo.getAmount());
        // map.put("total_amount","0.01");
        map.put("subject",paymentInfo.getContent());
        // map.put("time_expire","2025-03-29 17:09:00"); //设置订单绝对超时时间

        request.setBizContent(JSON.toJSONString(map));

        // return alipayClient.pageExecute(request).getBody(); //调用SDK生成表单;


        // 3. 发送请求
        AlipayTradeWapPayResponse resp = alipayClient.pageExecute(request, "POST");
        String h5From = resp.getBody();
        System.out.println("h5From====================================================================");
        System.out.println(h5From);
        System.out.println("h5From====================================================================");

        return h5From;

    }


//    @SneakyThrows
//    @Override
//    public String submitAlipay(String orderNo) {
//        //保存支付记录
//        PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(orderNo);
//        // 生产二维码
//        AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();//创建API对应的request
//        // 同步回调
//        // return_payment_url=http://sph-payment.atguigu.cn/alipay/callback/return
//        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
//        // 异步回调
//        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址
//        // 参数
//        // 声明一个map 集合
//        JSONObject bizContent = new JSONObject();
//        bizContent.put("out_trade_no", paymentInfo.getTradeNo());
//        bizContent.put("total_amount",paymentInfo.getAmount());
//        bizContent.put("subject","测试商品");
//        bizContent.put("product_code","QUICK_WAP_WAY");
//
//        alipayRequest.setBizContent(bizContent.toJSONString());
//
//        AlipayTradeWapPayResponse resp = alipayClient.pageExecute(alipayRequest, "POST");
//        String h5From = resp.getBody();
//        System.out.println("h5From====================================================================");
//        System.out.println(h5From);
//        System.out.println("h5From====================================================================");
//
//        return h5From;
//    }
}
