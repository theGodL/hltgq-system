package com.qgyun.hltgq.hltgqsystem.menu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 菜单模块 RestTemplate 配置：转发平台菜单接口专用实例（独立超时）。
 */
@Configuration
public class MenuApiConfig {

    @Bean
    public RestTemplate platformRestTemplate(RestTemplateBuilder builder,
                                             @Value("${menu.api-connect-timeout-ms:3000}") long connectTimeoutMs,
                                             @Value("${menu.api-read-timeout-ms:5000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
