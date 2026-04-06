package com.traincontroller.interceptor.transport;

import com.fazecast.jSerialComm.SerialPort;
import com.traincontroller.interceptor.config.InterceptorProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class JSerialCommSerialExchangeClient implements SerialExchangeClient {

    private final InterceptorProperties interceptorProperties;

    public JSerialCommSerialExchangeClient(InterceptorProperties interceptorProperties) {
        this.interceptorProperties = interceptorProperties;
    }

    @Override
    public String exchange(byte[] requestFrame, int timeoutMs) throws IOException {
        SerialPort port = SerialPort.getCommPort(interceptorProperties.serialPort());
        port.setComPortParameters(interceptorProperties.serialBaud(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutMs, timeoutMs);

        if (!port.openPort()) {
            throw new IOException("Unable to open serial port " + interceptorProperties.serialPort());
        }

        try (OutputStream out = port.getOutputStream(); InputStream in = port.getInputStream()) {
            out.write(requestFrame);
            out.flush();

            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            while (true) {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                responseBuffer.write(b);
                if (b == '\n') {
                    break;
                }
            }

            if (responseBuffer.size() == 0) {
                return null;
            }
            return responseBuffer.toString(StandardCharsets.US_ASCII);
        } finally {
            port.closePort();
        }
    }
}
