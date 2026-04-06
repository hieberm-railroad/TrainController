package com.traincontroller.interceptor.persistence;

public interface DeviceStateRepository {
    void upsert(DeviceStateEntity state);
}
