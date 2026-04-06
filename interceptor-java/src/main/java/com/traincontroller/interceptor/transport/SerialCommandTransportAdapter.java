package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SerialCommandTransportAdapter implements CommandTransportAdapter {

    private static final Logger log = LoggerFactory.getLogger(SerialCommandTransportAdapter.class);

    private final SerialExchangeClient serialExchangeClient;
    private final InterceptorProperties interceptorProperties;

    public SerialCommandTransportAdapter(
            SerialExchangeClient serialExchangeClient,
            InterceptorProperties interceptorProperties
    ) {
        this.serialExchangeClient = serialExchangeClient;
        this.interceptorProperties = interceptorProperties;
    }

    @Override
    public TransportSendResult send(TcCommandEntity command) {
        byte[] frame = SerialFrameCodec.encodeTurnoutCommand(
                command.nodeId(),
                command.commandId(),
                command.deviceId(),
                command.desiredState()
        );

        final String response;
        try {
            response = serialExchangeClient.exchange(frame, interceptorProperties.settleDelayMs());
        } catch (IOException ioError) {
            log.warn("Serial send failed commandId={} error={}", command.commandId(), ioError.getMessage());
            return TransportSendResult.transportError(ioError.getMessage());
        }

        if (response == null || response.isBlank()) {
            return TransportSendResult.noAck();
        }

        Optional<SerialFrameCodec.AckFrame> parsedAck = SerialFrameCodec.decodeAckFrame(response);
        if (parsedAck.isEmpty()) {
            return TransportSendResult.transportError("INVALID_ACK_FRAME");
        }

        SerialFrameCodec.AckFrame ackFrame = parsedAck.get();
        if (!command.nodeId().equals(ackFrame.nodeId())) {
            return TransportSendResult.transportError("ACK_NODE_MISMATCH");
        }

        if (!command.commandId().equals(ackFrame.commandId())) {
            return TransportSendResult.transportError("ACK_COMMAND_MISMATCH");
        }

        return TransportSendResult.ack(ackFrame.ackStatus());
    }
}
