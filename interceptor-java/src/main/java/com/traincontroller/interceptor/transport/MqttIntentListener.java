package com.traincontroller.interceptor.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.service.IntentService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
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
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.debug("MQTT message received topic={} payload={}", topic, payload);

        try {
            TurnoutIntent intent = payload.stripLeading().startsWith("{")
                    ? parseJsonIntent(topic, payload)
                    : parseNativeJmriIntent(topic, payload);

            if (intent == null) {
                return;
            }

            intentService.handle(intent);

        } catch (Exception e) {
            log.error("Failed to process MQTT message topic={} error={}", topic, e.getMessage(), e);
        }
    }

    private TurnoutIntent parseJsonIntent(String topic, String payload) throws Exception {
        TurnoutIntentPayload dto = objectMapper.readValue(payload, TurnoutIntentPayload.class);

        if (dto.commandId() == null || dto.turnoutId() == null || dto.desiredState() == null) {
            log.warn("Ignoring MQTT message with missing required fields topic={}", topic);
            return null;
        }

        TurnoutState desiredState = parseDesiredState(dto.desiredState(), topic);
        if (desiredState == null) {
            return null;
        }

        String turnoutId = normalizeTurnoutId(dto.turnoutId(), topic);
        if (turnoutId == null) {
            return null;
        }

        return new TurnoutIntent(
                dto.commandId(),
                dto.correlationId() != null ? dto.correlationId() : dto.commandId(),
                turnoutId,
                desiredState,
                null
        );
    }

    private TurnoutIntent parseNativeJmriIntent(String topic, String payload) {
        String turnoutId = extractTurnoutId(topic);
        if (turnoutId == null) {
            log.warn("Ignoring MQTT message with unexpected topic={} payload={}", topic, payload);
            return null;
        }

        turnoutId = normalizeTurnoutId(turnoutId, topic);
        if (turnoutId == null) {
            return null;
        }

        TurnoutState desiredState = parseDesiredState(payload, topic);
        if (desiredState == null) {
            return null;
        }

        String commandId = "jmri-" + UUID.randomUUID();
        return new TurnoutIntent(commandId, commandId, turnoutId, desiredState, null);
    }

    private String extractTurnoutId(String topic) {
        String normalizedTopic = topic == null ? "" : topic.strip();
        if (normalizedTopic.startsWith("/")) {
            normalizedTopic = normalizedTopic.substring(1);
        }

        String configuredPrefix = interceptorProperties.mqtt().inboundTopic().replace("+", "").strip();
        if (configuredPrefix.startsWith("/")) {
            configuredPrefix = configuredPrefix.substring(1);
        }

        if (!normalizedTopic.startsWith(configuredPrefix) || normalizedTopic.length() <= configuredPrefix.length()) {
            return null;
        }

        String turnoutId = normalizedTopic.substring(configuredPrefix.length());
        if (turnoutId.isBlank() || turnoutId.contains("/")) {
            return null;
        }

        return turnoutId;
    }

    private TurnoutState parseDesiredState(String rawValue, String topic) {
        String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "OPEN", "THROWN" -> TurnoutState.OPEN;
            case "CLOSED" -> TurnoutState.CLOSED;
            default -> {
                log.warn("Ignoring unknown desiredState={} topic={}", rawValue, topic);
                yield null;
            }
        };
    }

    private String normalizeTurnoutId(String rawTurnoutId, String topic) {
        if (rawTurnoutId == null) {
            log.warn("Ignoring MQTT message with null turnoutId topic={}", topic);
            return null;
        }

        String turnoutId = rawTurnoutId.trim();
        if (turnoutId.isEmpty()) {
            log.warn("Ignoring MQTT message with blank turnoutId topic={}", topic);
            return null;
        }

        if (turnoutId.chars().allMatch(Character::isDigit)) {
            try {
                int numeric = Integer.parseInt(turnoutId);
                if (numeric <= 0) {
                    log.warn("Ignoring MQTT message with non-positive numeric turnoutId={} topic={}", rawTurnoutId, topic);
                    return null;
                }
                return String.format(Locale.ROOT, "%03d", numeric);
            } catch (NumberFormatException ex) {
                log.warn("Ignoring MQTT message with invalid numeric turnoutId={} topic={}", rawTurnoutId, topic);
                return null;
            }
        }

        return turnoutId;
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
