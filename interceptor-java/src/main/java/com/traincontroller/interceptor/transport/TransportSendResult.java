package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.model.AckStatus;

public record TransportSendResult(
        AckStatus ackStatus,
        String transportError
) {
    public static TransportSendResult noAck() {
        return new TransportSendResult(null, null);
    }

    public static TransportSendResult ack(AckStatus ackStatus) {
        return new TransportSendResult(ackStatus, null);
    }

    public static TransportSendResult transportError(String transportError) {
        return new TransportSendResult(null, transportError);
    }

    public boolean hasAck() {
        return ackStatus != null;
    }

    public boolean hasTransportError() {
        return transportError != null && !transportError.isBlank();
    }
}
