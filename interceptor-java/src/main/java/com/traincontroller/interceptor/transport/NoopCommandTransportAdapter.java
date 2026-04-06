package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.persistence.TcCommandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopCommandTransportAdapter implements CommandTransportAdapter {

    private static final Logger log = LoggerFactory.getLogger(NoopCommandTransportAdapter.class);

    @Override
    public TransportSendResult send(TcCommandEntity command) {
        log.debug("Noop transport send commandId={} nodeId={}", command.commandId(), command.nodeId());
        return TransportSendResult.noAck();
    }
}
