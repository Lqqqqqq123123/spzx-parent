package com.spzx.cart.api;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.api.factory.RemoteCartFallbackFactory;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.constant.ServiceNameConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.web.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(contextId = "remoteCartService",
        value = ServiceNameConstants.CART_SERVICE,
        fallbackFactory = RemoteCartFallbackFactory.class)
public interface RemoteCartService {


    // 注意，这是内部服务的调用，所以不需要添加前面的cart，因为该请求不会被网关拦截，所以不会去除第一层路径
    @GetMapping("/getCartCheckedList/{userId}")
    public R<List<CartInfo>> getCartCheckedList(@PathVariable(value = "userId") Long userId, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);


    @GetMapping("/updateCartPrice/{userId}")
    public R<Boolean> updateCartPrice(@PathVariable("userId") Long userId, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);



    @GetMapping("/deleteCartCheckedList/{userId}")
    public R<Boolean> deleteCartCheckedList(@PathVariable("userId") Long userId, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}