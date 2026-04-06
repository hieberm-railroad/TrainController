package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.persistence.TcCommandEntity;

public interface CommandTransportAdapter {
    TransportSendResult send(TcCommandEntity command);
}
