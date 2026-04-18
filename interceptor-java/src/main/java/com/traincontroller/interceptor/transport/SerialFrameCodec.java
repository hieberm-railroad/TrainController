package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.model.TurnoutIntent;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

public final class SerialFrameCodec {

    public record AckFrame(String nodeId, String commandId, AckStatus ackStatus) {
    }

    public record StateFrame(String actualState) {
    }

    private SerialFrameCodec() {
    }

    public static byte[] encodeTurnoutCommand(String nodeId, TurnoutIntent intent) {
        return encodeTurnoutCommand(nodeId, intent.commandId(), intent.turnoutId(), intent.desiredState().name());
    }

    public static byte[] encodeTurnoutStateQuery(String nodeId) {
        return String.format("QSTATE|%s\n", nodeId).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeTurnoutCommand(String nodeId, String commandId, String turnoutId, String desiredState) {
        String payload = String.format("v1|%s|%s|TURNOUT|%s|%s", nodeId, commandId, turnoutId, desiredState);
        int checksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);
        String frame = payload + "|" + String.format("%02X", checksum) + "\n";
        return frame.getBytes(StandardCharsets.US_ASCII);
    }

    public static Optional<AckFrame> decodeAckFrame(String frame) {
        if (frame == null) {
            return Optional.empty();
        }

        String trimmed = frame.trim();
        String[] parts = trimmed.split("\\|");
        if (parts.length != 6) {
            return Optional.empty();
        }

        String version = parts[0];
        String nodeId = parts[1];
        String commandId = parts[2];
        String messageType = parts[3];
        String ackStatusRaw = parts[4];
        String checksumHex = parts[5];

        if (!"v1".equals(version) || !"ACK".equals(messageType)) {
            return Optional.empty();
        }

        String payload = String.join("|", parts[0], parts[1], parts[2], parts[3], parts[4]);
        int expectedChecksum = payload.chars().reduce(0, (acc, ch) -> (acc + ch) & 0xFF);

        int providedChecksum;
        try {
            providedChecksum = Integer.parseInt(checksumHex, 16);
        } catch (NumberFormatException invalidChecksum) {
            return Optional.empty();
        }
        if (expectedChecksum != providedChecksum) {
            return Optional.empty();
        }

        try {
            AckStatus ackStatus = AckStatus.valueOf(ackStatusRaw.toUpperCase(Locale.ROOT));
            return Optional.of(new AckFrame(nodeId, commandId, ackStatus));
        } catch (IllegalArgumentException invalidStatus) {
            return Optional.empty();
        }
    }

    public static Optional<StateFrame> decodeStateFrame(String frame) {
        if (frame == null || frame.isBlank()) {
            return Optional.empty();
        }

        String trimmed = frame.trim();
        String[] parts = trimmed.split("\\|");
        if (parts.length != 2 || !"STATE".equals(parts[0])) {
            return Optional.empty();
        }

        String actualState = parts[1].toUpperCase(Locale.ROOT);
        if (!"OPEN".equals(actualState) && !"CLOSED".equals(actualState)) {
            return Optional.empty();
        }
        return Optional.of(new StateFrame(actualState));
    }
}
