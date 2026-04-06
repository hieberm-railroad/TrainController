package com.traincontroller.interceptor;

import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.transport.SerialFrameCodec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialFrameCodecTest {

    @Test
    void encodesTurnoutFrameWithChecksumAndTerminator() {
        TurnoutIntent intent = new TurnoutIntent(
                "cmd-1",
                "corr-1",
                "1",
                TurnoutState.OPEN,
                Instant.parse("2026-04-04T00:00:00Z")
        );

        String frame = new String(SerialFrameCodec.encodeTurnoutCommand("node-1", intent), StandardCharsets.US_ASCII);

        assertTrue(frame.startsWith("v1|node-1|cmd-1|TURNOUT|1|OPEN|"));
        assertTrue(frame.endsWith("\n"));
    }

    @Test
    void decodesAckFrameWithValidChecksum() {
        String payload = "v1|node-1|cmd-1|ACK|ACCEPTED";
        int checksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);
        String frame = payload + "|" + String.format("%02X", checksum) + "\n";

        Optional<SerialFrameCodec.AckFrame> decoded = SerialFrameCodec.decodeAckFrame(frame);

        assertTrue(decoded.isPresent());
        assertEquals("node-1", decoded.get().nodeId());
        assertEquals("cmd-1", decoded.get().commandId());
        assertEquals("ACCEPTED", decoded.get().ackStatus().name());
    }

    @Test
    void rejectsAckFrameWithInvalidChecksum() {
        String frame = "v1|node-1|cmd-1|ACK|ACCEPTED|00\n";
        Optional<SerialFrameCodec.AckFrame> decoded = SerialFrameCodec.decodeAckFrame(frame);
        assertTrue(decoded.isEmpty());
    }

    @Test
    void encodesTurnoutStateQueryFrame() {
        String frame = new String(SerialFrameCodec.encodeTurnoutStateQuery(), StandardCharsets.US_ASCII);
        assertEquals("QSTATE\n", frame);
    }

    @Test
    void decodesLegacyStateFrame() {
        Optional<SerialFrameCodec.StateFrame> decoded = SerialFrameCodec.decodeStateFrame("STATE|OPEN\n");
        assertTrue(decoded.isPresent());
        assertEquals("OPEN", decoded.get().actualState());
    }

    @Test
    void rejectsInvalidStateFrame() {
        Optional<SerialFrameCodec.StateFrame> decoded = SerialFrameCodec.decodeStateFrame("STATE|MIDDLE\n");
        assertTrue(decoded.isEmpty());
    }
}
