package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import java.time.Instant;

public record TcCommandEntity(
        String commandId,
        String intentId,
        String correlationId,
        String deviceId,
        String nodeId,
        OperationType operationType,
        String desiredState,
        CommandStatus commandStatus,
        int retryCount,
        int maxRetries,
        int settleDelayMs,
        Instant nextAttemptAt,
        String failureReason
) {
}
