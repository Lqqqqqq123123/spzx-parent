package com.spzx.channel.configure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


@Configuration
public class ThreadPoolConfig{


    @Bean
    public ThreadPoolExecutor init(){
        // 1.获取当前系统的CPU核数
        int cpuCoreNum = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                cpuCoreNum * 2,
                cpuCoreNum * 2,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                Executors.defaultThreadFactory(),
                (Runnable r, ThreadPoolExecutor executor) ->{
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        executor.submit(r);
                    }

                }
        );

        pool.prestartCoreThread();
        return pool;
    }
}
