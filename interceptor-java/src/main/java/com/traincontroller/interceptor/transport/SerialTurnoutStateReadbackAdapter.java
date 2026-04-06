package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.metrics.InterceptorTelemetry;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SerialTurnoutStateReadbackAdapter implements TurnoutStateReadbackAdapter {

    private static final Logger log = LoggerFactory.getLogger(SerialTurnoutStateReadbackAdapter.class);

    private final SerialExchangeClient serialExchangeClient;
    private final InterceptorTelemetry interceptorTelemetry;

    public SerialTurnoutStateReadbackAdapter(
            SerialExchangeClient serialExchangeClient,
            InterceptorTelemetry interceptorTelemetry
    ) {
        this.serialExchangeClient = serialExchangeClient;
        this.interceptorTelemetry = interceptorTelemetry;
    }

    @Override
    public Optional<String> readActualState(TcCommandEntity command) {
        Instant readStartedAt = Instant.now();
        try {
            String response = serialExchangeClient.exchange(SerialFrameCodec.encodeTurnoutStateQuery(), command.settleDelayMs());
            Optional<String> decodedState = SerialFrameCodec.decodeStateFrame(response).map(SerialFrameCodec.StateFrame::actualState);
            interceptorTelemetry.recordReadbackResult(
                    decodedState.isPresent() ? "ok" : "invalid_frame",
                    Duration.between(readStartedAt, Instant.now())
            );
            return decodedState;
        } catch (IOException ioError) {
            interceptorTelemetry.recordReadbackResult("io_error", Duration.between(readStartedAt, Instant.now()));
            log.warn("Readback failed commandId={} nodeId={} error={}",
                    command.commandId(), command.nodeId(), ioError.getMessage());
            return Optional.empty();
        }
    }
}