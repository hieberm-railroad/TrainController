package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.persistence.TcCommandEntity;
import java.io.IOException;
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

    public SerialTurnoutStateReadbackAdapter(SerialExchangeClient serialExchangeClient) {
        this.serialExchangeClient = serialExchangeClient;
    }

    @Override
    public Optional<String> readActualState(TcCommandEntity command) {
        try {
            String response = serialExchangeClient.exchange(SerialFrameCodec.encodeTurnoutStateQuery(), command.settleDelayMs());
            return SerialFrameCodec.decodeStateFrame(response).map(SerialFrameCodec.StateFrame::actualState);
        } catch (IOException ioError) {
            log.warn("Readback failed commandId={} nodeId={} error={}",
                    command.commandId(), command.nodeId(), ioError.getMessage());
            return Optional.empty();
        }
    }
}