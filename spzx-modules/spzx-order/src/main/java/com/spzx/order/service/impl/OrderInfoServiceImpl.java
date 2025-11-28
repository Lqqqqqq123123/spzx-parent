package com.spzx.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.common.security.utils.SecurityUtils;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.OrderLog;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.order.service.IOrderItemService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.vo.SkuLockVo;
import com.spzx.product.api.domain.vo.SkuPrice;
import com.spzx.user.api.RemoteUserAddressService;
import com.spzx.user.domain.UserAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private IOrderItemService orderItemService;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private RemoteCartService remoteCartService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RemoteUserAddressService remoteUserAddressService;

    @Autowired
    RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单
     */
    @Override
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo) {
        return orderInfoMapper.selectOrderInfoList(orderInfo);
    }

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    @Override
    public OrderInfo selectOrderInfoById(Long id) {
        OrderInfo orderInfo = orderInfoMapper.selectById(id);
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }
    // 上面是后台接口
    // ===================================================================================================================

    @Override
    public TradeVo orderTradeData() {
        // 1. 获取当前用户id
        String userId = String.valueOf(SecurityUtils.getUserId());

        // 2. 获取当前用户的所选的购物车数据
        // 2.1 远程调用购物车模块的服务
        R<List<CartInfo>> result = remoteCartService.getCartCheckedList(Long.parseLong(userId), SecurityConstants.INNER);

        if(result.getCode() != R.SUCCESS){
            throw new ServiceException(result.getMsg());
        }

        // 健壮性处理
        List<CartInfo> cartInfoList = result.getData();
        if(CollectionUtils.isEmpty(cartInfoList)){
            throw new ServiceException("购物车为空");
        }
        // 3. 将该购物车数据转换成订单项数据
        List<OrderItem> list = cartInfoList.stream()
                .map(cartInfo -> {
                    OrderItem orderItem = new OrderItem();
                    orderItem.setSkuId(cartInfo.getSkuId());
                    orderItem.setSkuName(cartInfo.getSkuName());
                    orderItem.setSkuNum(cartInfo.getSkuNum());
                    orderItem.setSkuPrice(cartInfo.getSkuPrice());
                    orderItem.setThumbImg(cartInfo.getThumbImg());
                    return orderItem;
                }).toList();


        // 4. 计算订单总额
        BigDecimal totalAmount = list.stream()
                .map(orderItem -> orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. 封装数据
        String tradeNoKey = setTradeNoAndGetTradeNoKey(userId);
        TradeVo tradeVo = new TradeVo();
        tradeVo.setTradeNo(redisTemplate.opsForValue().get(tradeNoKey).toString());
        tradeVo.setOrderItemList(list);
        tradeVo.setTotalAmount(totalAmount);

        return tradeVo;
    }

    @Transactional
    @Override
    public Long submitOrder(OrderForm orderForm) {

        // 1. 校验是否提交重复 (下订单按钮不能重复点）
        String tradeNo =  orderForm.getTradeNo();
        String userId = String.valueOf(SecurityUtils.getUserId());
        // 1.1 首次提交删除缓存中的流水号，删除成功才能继续，删除失败就是：重复提交
        String tradeNoKey = getTradeNoKey(userId);


        // 保证删除 + 判断是原子的
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        // 1. 定义 Lua 脚本内容
        String script =
                "local deleted_count = redis.call('DEL', KEYS[1])\n" +
                        "return deleted_count";
        redisScript.setScriptText(script);
        redisScript.setResultType(Boolean.class);

        Boolean deleted = (Boolean) redisTemplate.execute(redisScript, Collections.singletonList(tradeNoKey));

        if(!deleted){
            throw new ServiceException("请勿重复提交订单");
        }

        // 2. 校验表单数据是否为空
        // 3. 校验价格是否变化
        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        List<Long> ids = orderItemList.stream().map(OrderItem::getSkuId).toList();

        // 3.1 批量查询最新价格
        R<List<SkuPrice>> result = remoteProductService.getSkuPriceList(ids, SecurityConstants.INNER);

        // 降级处理
        if(result.getCode() != R.SUCCESS){
            throw new ServiceException(result.getMsg());
        }
        List<SkuPrice> skuPriceList = result.getData();
        Map<Long, BigDecimal> idToSkuPrice = skuPriceList
                .stream()
                .collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));

        StringBuilder errorMsg = new StringBuilder();
        orderItemList.forEach(orderItem -> {
            BigDecimal skuPrice = idToSkuPrice.get(orderItem.getSkuId());
            if(orderItem.getSkuPrice().compareTo(skuPrice) != 0 ){
                // 3.2 记录信息
                errorMsg.append("商品：").append(orderItem.getSkuName()).append("价格有变化，请重新下单");
            }
        });


        // 3.3 如果价格不一致
        if(StringUtils.hasText(errorMsg.toString())){

            // 3.4 更新购物车价格
            remoteCartService.updateCartPrice(Long.parseLong(userId), SecurityConstants.INNER);

            // 3.5 抛异常,终止本次下单
            throw new ServiceException(errorMsg.toString());
        }


        // 4. 校验与锁定库存 必须同步请求
        // 先去商品微服务写接口：检查与锁定库存
        List<SkuLockVo> skuLockVoList = orderItemList.stream()
                        .map(orderItem -> {
                            SkuLockVo skuLockVo = new SkuLockVo();
                            skuLockVo.setSkuId(orderItem.getSkuId());
                            skuLockVo.setSkuNum(orderItem.getSkuNum());
                            return skuLockVo;
                        }).toList();

        R<String> stringR = remoteProductService.checkAndLock(orderForm.getTradeNo(), skuLockVoList, SecurityConstants.INNER);
        if(stringR.getCode() != R.SUCCESS){
            throw new ServiceException(stringR.getMsg());
        }

        String checkResult = stringR.getData();
        if(StringUtils.hasText(checkResult)){
            // 4.1 锁定库存失败
            // 4.1.1 解锁库存（自动回滚）
            // 4.1.2 抛异常
            throw new ServiceException(checkResult);
        }



        Long orderId = -1L;
        // 5. 保存订单
        try {
            orderId = this.saveOrder(orderForm);
        } catch (Exception e) {
            // todo 解锁库存
            rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT, MqConst.ROUTING_UNLOCK, orderForm.getTradeNo());
            // 解锁异步也无所谓，所以可以用小兔子
            throw new ServiceException("保存订单失败");
        }


        // 6. 从购物车删除当前下单的商品
        remoteCartService.deleteCartCheckedList(Long.parseLong(userId), SecurityConstants.INNER);


        // 7. 发送一个延迟消息：30分钟后判断用户是否已支付，没支付需要也需要解锁库存
        System.out.println("发送到延迟队列:" + new Date());
        rabbitService.sendDealyMessage(MqConst.ROUTING_CANCEL_ORDER,
                MqConst.ROUTING_CANCEL_ORDER,
                String.valueOf(orderId), MqConst.CANCEL_ORDER_DELAY_TIME);


        // 8. 返回订单id
        return orderId;
    }

    @Override
    @Transactional
    public void processCloseOrder(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(null != orderInfo && orderInfo.getOrderStatus().intValue() == 0) { //  订单状态orderStatus=0 说明15分钟未支付。
            orderInfo.setOrderStatus(-1); //  -1 取消订单
            orderInfo.setCancelTime(new Date());
            orderInfo.setCancelReason("未支付自动取消");
            orderInfoMapper.updateById(orderInfo);

            //记录日志
            OrderLog orderLog = new OrderLog();
            orderLog.setOrderId(orderInfo.getId());
            orderLog.setProcessStatus(-1);
            orderLog.setNote("系统取消订单");
            orderLogMapper.insert(orderLog);

            //发送MQ消息通知商品系统解锁库存
            rabbitService.sendMessage(MqConst.EXCHANGE_PRODUCT, MqConst.ROUTING_UNLOCK, orderInfo.getOrderNo());
        }
    }

    @Override
    public OrderInfo getByOrderNo(String orderNo) {
        // 1. 根据订单号查询订单
        OrderInfo orderinfo = orderInfoMapper.selectOne(new QueryWrapper<OrderInfo>().eq("order_no", orderNo));

        // 2. 根据订单号查询订单项列表
        LambdaQueryWrapper<OrderItem> wr1 = new LambdaQueryWrapper<>();
        wr1.eq(OrderItem::getOrderId, orderinfo.getId());
        List<OrderItem> orderItemList = orderItemMapper.selectList(wr1);
        // 3. 封装数据
        orderinfo.setOrderItemList(orderItemList);

        return orderinfo;
    }

    /*
    支付成功，将订单状态修改为 1 待发货
     */
    @Override
    public void processPaySucess(String orderNo) {
        // 记得判重
        // 1. 查询订单信息
        LambdaQueryWrapper<OrderInfo> wr = new LambdaQueryWrapper<>();
        wr.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(wr);

        // 如果当前订单状态已经是支付成功了，那就直接返回即可
        if(orderInfo.getOrderStatus().intValue() != 0){
            return ;
        }

        orderInfo.setOrderStatus(1);
        orderInfo.setUpdateTime(new Date());

        // 设置支付时间
        orderInfo.setPaymentTime(new Date());

        orderInfoMapper.updateById(orderInfo);

    }

//    @Override
//    public OrderInfo getByOrderNo(String orderNo) {
//        OrderInfo orderInfo = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
//        List <OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderInfo.getId()));
//        orderInfo.setOrderItemList(orderItemList);
//        return orderInfo;
//    }

    @Transactional
    public Long saveOrder(OrderForm orderForm) {
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        // 保存至订单表
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(orderForm.getTradeNo());
        orderInfo.setUserId(userId);
        orderInfo.setNickName(userName);
        orderInfo.setRemark(orderForm.getRemark());
        UserAddress userAddress = remoteUserAddressService.getUserAddress(orderForm.getUserAddressId(), SecurityConstants.INNER).getData();
        orderInfo.setReceiverName(userAddress.getName());
        orderInfo.setReceiverPhone(userAddress.getPhone());
        orderInfo.setReceiverTagName(userAddress.getTagName());
        orderInfo.setReceiverProvince(userAddress.getProvinceCode());
        orderInfo.setReceiverCity(userAddress.getCityCode());
        orderInfo.setReceiverDistrict(userAddress.getDistrictCode());
        orderInfo.setReceiverAddress(userAddress.getFullAddress());

        BigDecimal total = orderForm.getOrderItemList().stream()
                        .map(orderItem -> {
                            return orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum()));
                        }).reduce(new BigDecimal(0), BigDecimal::add);

        orderInfo.setTotalAmount(total); // 实际付款
        orderInfo.setCouponAmount(new BigDecimal(0));
        orderInfo.setOriginalTotalAmount(total); // 商品总价
        orderInfo.setOrderStatus(0);
        orderInfoMapper.insert(orderInfo);

        // 保存订单项表
        List<OrderItem> orderItemList = orderForm.getOrderItemList();

        orderItemList.forEach(orderItem -> {
            orderItem.setOrderId(orderInfo.getId()); // 设置外键即可
            orderItemMapper.insert(orderItem);
        });


        // 保存至订单日志表
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setOperateUser("用户");
        orderLog.setProcessStatus(0);
        orderLog.setNote("用户下单");
        orderLogMapper.insert(orderLog);



        // 返回订单id
        return orderInfo.getId();
    }






    private String setTradeNoAndGetTradeNoKey(String userId){
        // 1.1  拼个键，然后生成一个流水号，也就是订单号，然后存到redis中
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        String tradeNoKey = "order:tradeNo:" + userId;
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo, 60 * 5, TimeUnit.SECONDS);
        return tradeNoKey;
    }

    private String getTradeNoKey(String userId){
        return "order:tradeNo:" + userId;
    }

}