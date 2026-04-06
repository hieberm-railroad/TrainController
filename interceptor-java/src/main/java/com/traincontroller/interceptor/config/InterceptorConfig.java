package com.traincontroller.interceptor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(InterceptorProperties.class)
@EnableScheduling
public class InterceptorConfig {
}
