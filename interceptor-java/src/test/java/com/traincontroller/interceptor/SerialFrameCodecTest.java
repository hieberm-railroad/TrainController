package com.traincontroller.interceptor;

import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.transport.SerialFrameCodec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

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
}
