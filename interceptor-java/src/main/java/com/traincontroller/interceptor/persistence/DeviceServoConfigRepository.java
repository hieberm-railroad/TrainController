package com.traincontroller.interceptor.persistence;

import java.util.Optional;

public interface DeviceServoConfigRepository {
    Optional<DeviceServoConfig> findByDeviceId(String deviceId);
}
