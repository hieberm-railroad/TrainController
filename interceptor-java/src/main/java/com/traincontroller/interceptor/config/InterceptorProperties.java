package com.traincontroller.interceptor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "interceptor")
public record InterceptorProperties(
        int settleDelayMs,
        int maxRetries,
        int retryBackoffMs,
        String serialPort,
        int serialBaud,
        Mqtt mqtt
) {
    public record Mqtt(
            String brokerUri,
            String clientId,
            String inboundTopic
    ) {
    }
}
