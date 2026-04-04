package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.model.TurnoutIntent;
import java.nio.charset.StandardCharsets;

public final class SerialFrameCodec {

    private SerialFrameCodec() {
    }

    public static byte[] encodeTurnoutCommand(String nodeId, TurnoutIntent intent) {
        String payload = String.format("v1|%s|%s|TURNOUT|%s|%s", nodeId, intent.commandId(), intent.turnoutId(), intent.desiredState());
        int checksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);
        String frame = payload + "|" + String.format("%02X", checksum) + "\n";
        return frame.getBytes(StandardCharsets.US_ASCII);
    }
}
