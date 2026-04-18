package com.traincontroller.interceptor.transport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Inbound MQTT JSON payload for a turnout intent.
 *
 * <p>Expected format:
 * <pre>
 * {
 *   "commandId":    "cmd-abc-123",
 *   "correlationId":"corr-xyz-456",
 *   "turnoutId":    "turnout1",
 *   "desiredState": "OPEN"
 * }
 * </pre>
 * Unknown fields are silently ignored so that upstream systems can include extras.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnoutIntentPayload(
        String commandId,
        String correlationId,
        String turnoutId,
        String desiredState
) {
}
