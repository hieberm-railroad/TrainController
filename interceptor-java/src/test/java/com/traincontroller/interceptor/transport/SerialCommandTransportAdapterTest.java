package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SerialCommandTransportAdapterTest {

    @Mock
    private SerialExchangeClient serialExchangeClient;

    @Test
    void sendMapsValidAckResponse() throws Exception {
        SerialCommandTransportAdapter adapter = new SerialCommandTransportAdapter(
                serialExchangeClient,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
        );

        String payload = "v1|turnout-12|cmd-123|ACK|ACCEPTED";
        int checksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);
        String response = payload + "|" + String.format("%02X", checksum) + "\n";

        when(serialExchangeClient.exchange(any(), anyInt())).thenReturn(response);

        TransportSendResult result = adapter.send(command("cmd-123"));

        assertEquals(AckStatus.ACCEPTED, result.ackStatus());
        assertEquals(null, result.transportError());
    }

    @Test
    void sendReturnsTransportErrorOnInvalidAckFrame() throws Exception {
        SerialCommandTransportAdapter adapter = new SerialCommandTransportAdapter(
                serialExchangeClient,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
        );

        when(serialExchangeClient.exchange(any(), anyInt())).thenReturn("bad-frame\n");

        TransportSendResult result = adapter.send(command("cmd-123"));

        assertTrue(result.hasTransportError());
        assertEquals("INVALID_ACK_FRAME", result.transportError());
    }

    @Test
    void sendReturnsTransportErrorOnAckCommandMismatch() throws Exception {
        SerialCommandTransportAdapter adapter = new SerialCommandTransportAdapter(
                serialExchangeClient,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
        );

        String payload = "v1|turnout-12|cmd-other|ACK|ACCEPTED";
        int checksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);
        String response = payload + "|" + String.format("%02X", checksum) + "\n";
        when(serialExchangeClient.exchange(any(), anyInt())).thenReturn(response);

        TransportSendResult result = adapter.send(command("cmd-123"));

        assertTrue(result.hasTransportError());
        assertEquals("ACK_COMMAND_MISMATCH", result.transportError());
    }

    @Test
    void sendReturnsTransportErrorOnAckNodeMismatch() throws Exception {
        SerialCommandTransportAdapter adapter = new SerialCommandTransportAdapter(
                serialExchangeClient,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
        );

        String payload = "v1|turnout-99|cmd-123|ACK|ACCEPTED";
        int checksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);
        String response = payload + "|" + String.format("%02X", checksum) + "\n";
        when(serialExchangeClient.exchange(any(), anyInt())).thenReturn(response);

        TransportSendResult result = adapter.send(command("cmd-123"));

        assertTrue(result.hasTransportError());
        assertEquals("ACK_NODE_MISMATCH", result.transportError());
    }

    @Test
    void sendReturnsNoAckWhenNoResponse() throws Exception {
        SerialCommandTransportAdapter adapter = new SerialCommandTransportAdapter(
                serialExchangeClient,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
        );

        when(serialExchangeClient.exchange(any(), anyInt())).thenReturn(null);

        TransportSendResult result = adapter.send(command("cmd-123"));

        assertTrue(!result.hasAck());
        assertTrue(!result.hasTransportError());
    }

    private static TcCommandEntity command(String commandId) {
        return new TcCommandEntity(
                commandId,
                "intent-1",
                "corr-1",
                "turnout-12",
                "turnout-12",
                OperationType.TURNOUT_SET,
                "OPEN",
                CommandStatus.SENT,
                0,
                5,
                750,
                null,
                null
        );
    }
}
