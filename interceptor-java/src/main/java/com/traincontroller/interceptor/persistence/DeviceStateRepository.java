package com.traincontroller.interceptor.persistence;

import java.util.Optional;

public interface DeviceStateRepository {
    void upsert(DeviceStateEntity state);

    Optional<DeviceStateEntity> findLatestByDeviceId(String deviceId);
}
