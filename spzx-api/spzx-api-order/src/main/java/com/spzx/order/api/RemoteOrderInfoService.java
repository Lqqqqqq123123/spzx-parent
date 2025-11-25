package com.spzx.order.api;

import com.spzx.common.core.constant.ServiceNameConstants;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.order.api.factory.RemoteOrderInfoFallbackFactory;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(contextId = "remoteUserInfoService", value = ServiceNameConstants.ORDER_SERVICE, fallbackFactory = RemoteOrderInfoFallbackFactory.class)
public interface RemoteOrderInfoService {

}