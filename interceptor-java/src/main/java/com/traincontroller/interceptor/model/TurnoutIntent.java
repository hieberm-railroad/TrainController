package com.traincontroller.interceptor.model;

import java.time.Instant;

public record TurnoutIntent(
        String commandId,
        String correlationId,
        String turnoutId,
        TurnoutState desiredState,
        Instant createdAt
) {
}
