package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.IntentStatus;
import com.traincontroller.interceptor.model.OperationType;
import java.time.Instant;

public record IntentEntity(
        String intentId,
        String correlationId,
        String sourceSystem,
        String deviceId,
        OperationType operationType,
        String desiredState,
        Instant requestedAt,
        Instant acceptedAt,
        IntentStatus status,
        String rejectReason
) {
}
