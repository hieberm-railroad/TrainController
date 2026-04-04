package com.traincontroller.interceptor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InterceptorProperties.class)
public class InterceptorConfig {
}
