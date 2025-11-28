package com.spzx.report;

import com.spzx.common.security.annotation.EnableCustomConfig;
import com.spzx.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class SpzxAiReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpzxAiReportApplication.class, args);
    }
}