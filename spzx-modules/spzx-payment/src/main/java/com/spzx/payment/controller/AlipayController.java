package com.spzx.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.common.security.annotation.RequiresLogin;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/alipay")
public class AlipayController extends BaseController {

    @Autowired
    private IAlipayService alipayService;

    @Autowired
    private IPaymentInfoService paymentInfoService;

    @Autowired
    RabbitService rabbitService;



    @Operation(summary = "支付宝下单")
    @RequiresLogin
    @GetMapping("submitAlipay/{orderNo}")

    public AjaxResult submitAlipay(@PathVariable(value = "orderNo") String orderNo) {

        // 返回 h5 表单，用于打开手机的支付宝
        String form = alipayService.submitAlipay(orderNo);

        return success(form);
    }

    /**
     * 支付宝异步回调对接
     * @param paramMap
     * @param request
     * @return
     */
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap, HttpServletRequest request) throws ParseException {

        // 验签
        log.info("AlipayController...alipayNotify方法执行了...");
        boolean signVerified = false; //调用SDK验证签名
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        if(signVerified){
            String trade_status = paramMap.get("trade_status");
            if("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                // 1. 修改支付信息
                paymentInfoService.customUpdatePaymentInfo(paramMap, 2); // pay_type = 2 支付宝支付
                // 2. 修改订单状态
                rabbitService.sendMessage(MqConst.EXCHANGE_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY, paramMap.get("out_trade_no"));
                // 3. 减库存
                rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT, MqConst.ROUTING_MINUS, paramMap.get("out_trade_no"));

                return "success" ;
            }
            else{
                return "failure";
            }
        }else{
            return "failure";
        }
    }

}