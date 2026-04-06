package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.StateQuality;
import java.time.Instant;

public record DeviceStateEntity(
        String deviceId,
        String desiredState,
        String actualState,
        String lastCommandId,
        StateQuality stateQuality,
        Instant lastVerifiedAt
) {
}
