package com.spzx.cart.service.impl;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.common.security.utils.SecurityUtils;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.product.api.domain.vo.SkuPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Override
    public void addToCart(Long skuId, Integer skuNum) {
        // 1. 拿到当前用户信息,拼键
        String key = "user:cart:" + SecurityUtils.getUserId();
        String hashKey = skuId.toString();
        // todo 添加购物车信息调试
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(key);
        // 1.1 判断redis里是否有数据
        if(boundHashOperations.hasKey(hashKey)){
            // 1.2 获取redis中的数据
            CartInfo cartInfo = (CartInfo) boundHashOperations.get(hashKey);

            // 1.3 添加增量
            Integer curNum =  cartInfo.getSkuNum() + skuNum;
            if(curNum < 0) curNum = 0;
            if(curNum > 99) curNum = 99;
            cartInfo.setSkuNum(curNum);
            cartInfo.setUpdateTime(new Date());

            // 1.4 放回redis中
            boundHashOperations.put(hashKey,cartInfo);
            return;
        }

        // 2. 首次添加，获取购物车数据，注意，商品类别不超过50个，也就是这个hash键最多有50个
        if(boundHashOperations.size() >= 50){
            throw new ServiceException("购物车已满50个");
        }
        CartInfo cartinfo = new CartInfo();
        cartinfo.setUserId(SecurityUtils.getUserId());
        cartinfo.setSkuId(skuId);
        // 2.1 第一次添加购物车默认为添加1件
        cartinfo.setSkuNum(1);

        // 2.2 远程结构调用去获得当前 sku 的详细信息
        R<ProductSku> result = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);

        if(result.getCode() == R.SUCCESS){
            ProductSku productSku = result.getData();
            cartinfo.setCartPrice(productSku.getSalePrice());
            cartinfo.setSkuPrice(productSku.getSalePrice());
            cartinfo.setThumbImg(productSku.getThumbImg());
            cartinfo.setSkuName(productSku.getSkuName());
            cartinfo.setCreateTime(new Date());
        }else{
            log.error("添加购物车失败");
            throw new ServiceException(result.getCode() + ":" + result.getMsg());
        }

        // 3. 封装数据

        boundHashOperations.put(hashKey,cartinfo);

    }

    @Override
    public List<CartInfo> getCartList() {
        // 1. 拿到当前用户信息，然后拼键，
        String key = getUserCartKey(SecurityUtils.getUserId());

        // 2. 去redis中获取当前用户的购物车数据
        List<CartInfo> cartinfoList = redisTemplate.opsForHash().values(key);
        // todo 获取购物车信息调试
        log.info(cartinfoList.toString());

        if(CollectionUtils.isEmpty(cartinfoList)){
            log.info("用户没有购物车数据");
            return new ArrayList<>();
        }
        // 2.1 按创建时间排序
        List<CartInfo> cartInfo = cartinfoList
                .stream()
                .sorted((o1, o2) ->
                        o2.getCreateTime().compareTo(o1.getCreateTime())
                )
                .collect(Collectors.toList());




        // 3. 把商品的 sku 放到一个集合里
        List<Long> ids = cartInfo
                .stream()
                .map(CartInfo::getSkuId)
                .toList();


        // 4. 远程调用，获取这些商品的实时价格
        R<List<SkuPrice>> skuPriceListResult = remoteProductService.getSkuPriceList(ids, SecurityConstants.INNER);

        if(skuPriceListResult.getCode() == R.FAIL){
            throw new ServiceException(skuPriceListResult.getMsg());
        }

        // 5. 更新购物车数据
        Map<Long, BigDecimal> skuPriceMap = skuPriceListResult
                .getData()
                .stream()
                .collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));


        cartInfo.forEach(cur -> {
            // 设置实时价格
            cur.setSkuPrice(skuPriceMap.get(cur.getSkuId()));
        });

        return cartInfo;
    }

    @Override
    public void deleteCart(Long skuId) {
        String key = getUserCartKey(SecurityUtils.getUserId());

        BoundHashOperations hashOps = redisTemplate.boundHashOps(key);
        if(hashOps.hasKey(String.valueOf(skuId))){
            hashOps.delete(String.valueOf(skuId));
        }
    }

    @Override
    public void checkCart(Long skuId, Integer isChecked) {
        // 1. 还是取到当前用户信息，拼键
        String key = getUserCartKey(SecurityUtils.getUserId());

        // 2. 获取当前用户的购物车数据
        BoundHashOperations hashOps = redisTemplate.boundHashOps(key);

        if(hashOps.hasKey(String.valueOf(skuId))){
            // 3. 拿到当前商品数据
            CartInfo cartInfo = (CartInfo) hashOps.get(skuId.toString());
            // 4. 修改状态
            cartInfo.setIsChecked(isChecked);
            // 5. 放回redis中
            hashOps.put(String.valueOf(skuId),cartInfo);

            log.info("修改状态成功");

            return;
        }

        log.info("修改购物车商品状态失败");
        throw new ServiceException("修改购物车商品状态失败");
    }

    @Override
    public void allCheckCart(Integer isChecked) {
        // 1. 拿到当前用户信息，拼键
        String key = getUserCartKey(SecurityUtils.getUserId());

        // 2. 获取当前用户的购物车数据
        BoundHashOperations hashOps = redisTemplate.boundHashOps(key);

        HashMap<String, CartInfo> cartInfoMap = (HashMap<String, CartInfo>) hashOps.entries();

        if(cartInfoMap != null){
            cartInfoMap.forEach((skuId,cartInfo) -> {
                cartInfo.setIsChecked(isChecked);
                hashOps.put(skuId,cartInfo);
            });

            log.info("修改全部状态成功");
        }
    }

    @Override
    public void clearCart() {
        // 1. 拿到当前用户信息，拼键
        String key = getUserCartKey(SecurityUtils.getUserId());
        redisTemplate.delete(key);
    }

    @Override
    public List<CartInfo> getCartCheckedList(Long userId) {
        // 1. 获取当前用户信息，拼键
        String key = getUserCartKey(userId);

        // 2. 获取当前用户的购物车数据
        BoundHashOperations hashOps = redisTemplate.boundHashOps(key);

        // 3. 获取被选中的数据
        List<CartInfo> cartIsSelected = new ArrayList<>();

        List<CartInfo> cartInfoList = hashOps.values();
        cartInfoList.forEach(cartInfo -> {
            if(cartInfo.getIsChecked() == 1){
                cartIsSelected.add(cartInfo);
            }
        });

        return cartIsSelected;
    }

    @Override
    public Boolean updateCartPrice(Long userId) {
        // 1. 获取当前用户信息，拼键
        String key = getUserCartKey(userId);

        // 2. 获取当前用户的购物车数据
        BoundHashOperations hashOps = redisTemplate.boundHashOps(key);
        List<CartInfo> cartInfoList = hashOps.values();
        if(CollectionUtils.isEmpty(cartInfoList)){
            throw new ServiceException("当前用户购物车为空");
        }
        // 3. 获取这些商品的id，然后批量查询最新价格
        List<Long> ids = cartInfoList
                    .stream()
                    .filter((cartInfo -> cartInfo.getIsChecked() == 1))
                    .map(CartInfo::getSkuId)
                    .toList();


        // 3.1 远程调用，批量查询最新价格
        R<List<SkuPrice>> skuPriceListResult = remoteProductService.getSkuPriceList(ids, SecurityConstants.INNER);
        // 降级处理
        if(skuPriceListResult.getCode() == R.FAIL){
            throw new ServiceException("远程调用remoteProductService.getSkuPriceList失败");
        }

        // 3.2 更新价格，只把选中的商品的价格更新
        Map<Long, BigDecimal> skuPriceMap = skuPriceListResult.getData().stream().collect(Collectors.toMap(SkuPrice::getSkuId, SkuPrice::getSalePrice));


        cartInfoList.forEach(cartInfo -> {
            if(cartInfo.getIsChecked() == 1){
                String skuId = cartInfo.getSkuId().toString();
                BigDecimal curPrice =  skuPriceMap.get(cartInfo.getSkuId());
                cartInfo.setSkuPrice(curPrice);
                cartInfo.setCartPrice(curPrice);

                // 4. 写回redis
                hashOps.put(skuId,cartInfo);
            }


        });
        return true;
    }

    @Override
    public Boolean deleteCartCheckedList(Long userId) {
        // 1. 获取当前用户信息，拼键
        String key = getUserCartKey(userId);

        // 2. 获取当前用户的购物车中被选中的数据
        BoundHashOperations hashOps = redisTemplate.boundHashOps(key);

        List<CartInfo> list = hashOps.values();

        if(CollectionUtils.isEmpty(list)){
            throw new ServiceException("当前用户购物车为空");
        }

       list.forEach(cartInfo -> {
           if(cartInfo.getIsChecked() == 1){
               hashOps.delete(cartInfo.getSkuId().toString());
           }
       });

        return true;
    }

    private String getUserCartKey(Long userId){
        return "user:cart:" + userId;
    }
}