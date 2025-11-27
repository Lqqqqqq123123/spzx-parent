package com.spzx.order.configure;

import com.spzx.common.rabbit.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableRabbit // 👈 确保加上
public class CancelOrderMqConfig {

    @Bean
    public Queue cancelQueue() {
        // 第一个参数是创建的queue的名字，第二个参数是是否支持持久化
        return new Queue(MqConst.QUEUE_CANCEL_ORDER, true);
    }

    @Bean
    public CustomExchange cancelExchange() {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(MqConst.EXCHANGE_CANCEL_ORDER, "x-delayed-message", true, false, args);
    }

    @Bean
    public Binding bindingCancel() {
        return BindingBuilder.bind(cancelQueue()).to(cancelExchange()).with(MqConst.ROUTING_CANCEL_ORDER).noargs();
    }

}