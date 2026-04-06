package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.AckStatus;
import java.time.Instant;

public interface CommandAttemptRepository {
    void insertSentAttempt(String commandId, int attemptNo, Instant sentAt);

    int markAck(String commandId, int attemptNo, AckStatus ackStatus, Instant ackedAt);

    int markTransportError(String commandId, int attemptNo, String transportError);
}
