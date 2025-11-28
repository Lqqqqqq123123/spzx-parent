package com.spzx.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.payment.domain.PaymentInfo;

import java.text.ParseException;
import java.util.Map;

/**
 * 付款信息Service接口
 */
public interface IPaymentInfoService extends IService<PaymentInfo> {


    /**
     * 保存付款信息（判重）
     * @return
     */
    PaymentInfo savePaymentInfo(String orderNo);

    void customUpdatePaymentInfo(Map<String, String> paramMap, int i) throws ParseException;
}