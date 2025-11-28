package com.spzx.payment.service;

public interface IAlipayService {

    /**
     * 支付宝下单
     * @param orderNo
     * @return
     */
    String submitAlipay(String orderNo);
}
