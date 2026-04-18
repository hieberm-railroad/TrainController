package com.traincontroller.interceptor.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.service.IntentService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttIntentListener implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttIntentListener.class);

    private final InterceptorProperties interceptorProperties;
    private final IntentService intentService;
    private final ObjectMapper objectMapper;

    private MqttClient mqttClient;

    public MqttIntentListener(
            InterceptorProperties interceptorProperties,
            IntentService intentService,
            ObjectMapper objectMapper
    ) {
        this.interceptorProperties = interceptorProperties;
        this.intentService = intentService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() throws MqttException {
        mqttClient = new MqttClient(
                interceptorProperties.mqtt().brokerUri(),
                interceptorProperties.mqtt().clientId()
        );
        mqttClient.setCallback(this);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);

        mqttClient.connect(options);
        mqttClient.subscribe(interceptorProperties.mqtt().inboundTopic(), 1);

        log.info("MQTT listener connected broker={} topic={}",
                interceptorProperties.mqtt().brokerUri(),
                interceptorProperties.mqtt().inboundTopic());
    }

    @PreDestroy
    public void stop() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                log.warn("Error disconnecting MQTT client: {}", e.getMessage());
            }
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        log.debug("MQTT message received topic={} payload={}", topic, payload);

        try {
            TurnoutIntentPayload dto = objectMapper.readValue(payload, TurnoutIntentPayload.class);

            if (dto.commandId() == null || dto.turnoutId() == null || dto.desiredState() == null) {
                log.warn("Ignoring MQTT message with missing required fields topic={}", topic);
                return;
            }

            TurnoutState desiredState;
            try {
                desiredState = TurnoutState.valueOf(dto.desiredState().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring unknown desiredState={} topic={}", dto.desiredState(), topic);
                return;
            }

            TurnoutIntent intent = new TurnoutIntent(
                    dto.commandId(),
                    dto.correlationId() != null ? dto.correlationId() : dto.commandId(),
                    dto.turnoutId(),
                    desiredState,
                    null
            );

            intentService.handle(intent);

        } catch (Exception e) {
            log.error("Failed to process MQTT message topic={} error={}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used for inbound-only listener.
    }
}
