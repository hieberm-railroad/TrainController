package com.traincontroller.interceptor.transport;

import java.io.IOException;

public interface SerialExchangeClient {
    String exchange(byte[] requestFrame, int timeoutMs) throws IOException;
}
