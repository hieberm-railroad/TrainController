package com.traincontroller.interceptor.transport;

import com.fazecast.jSerialComm.SerialPort;
import com.traincontroller.interceptor.config.InterceptorProperties;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class JSerialCommSerialExchangeClient implements SerialExchangeClient {

    private static final Duration PORT_OPEN_STABILIZATION_DELAY = Duration.ofMillis(250);

    private final InterceptorProperties interceptorProperties;

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    public JSerialCommSerialExchangeClient(InterceptorProperties interceptorProperties) {
        this.interceptorProperties = interceptorProperties;
    }

    @Override
    public synchronized String exchange(byte[] requestFrame, int timeoutMs) throws IOException {
        ensurePortOpen(timeoutMs);

        try {
            while (in.available() > 0) {
                in.read();
            }

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
        } catch (IOException e) {
            closePortQuietly();
            throw e;
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        closePortQuietly();
    }

    private void ensurePortOpen(int timeoutMs) throws IOException {
        if (port != null && port.isOpen() && in != null && out != null) {
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutMs, timeoutMs);
            return;
        }

        port = SerialPort.getCommPort(interceptorProperties.serialPort());
        port.setComPortParameters(interceptorProperties.serialBaud(), 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeoutMs, timeoutMs);

        if (!port.openPort()) {
            throw new IOException("Unable to open serial port " + interceptorProperties.serialPort());
        }

        try {
            Thread.sleep(PORT_OPEN_STABILIZATION_DELAY.toMillis());
            in = port.getInputStream();
            out = port.getOutputStream();
            // Discard any startup banner/noise after opening the port.
            while (in.available() > 0) {
                in.read();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closePortQuietly();
            throw new IOException("Interrupted while opening serial port", e);
        } catch (IOException e) {
            closePortQuietly();
            throw e;
        }
    }

    private void closePortQuietly() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        }

        if (port != null && port.isOpen()) {
            port.closePort();
        }

        in = null;
        out = null;
        port = null;
    }
}
