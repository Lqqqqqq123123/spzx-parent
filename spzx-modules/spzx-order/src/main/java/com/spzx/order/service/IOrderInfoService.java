package com.spzx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;

import java.util.List;

public interface IOrderInfoService extends IService<OrderInfo> {
    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单集合
     */
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo);

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    public OrderInfo selectOrderInfoById(Long id);

    /**
     * 结算页面数据
     * @return
     */
    TradeVo orderTradeData();

    Long submitOrder(OrderForm orderForm);

    /**
     * 接收到了rmq的延迟消息，关闭订单
     * @param l
     */
    void processCloseOrder(Long l);

    OrderInfo getByOrderNo(String orderNo);

    void processPaySucess(String orderNo);
}