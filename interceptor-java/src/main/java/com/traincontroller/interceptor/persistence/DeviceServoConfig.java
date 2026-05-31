package com.traincontroller.interceptor.persistence;

public record DeviceServoConfig(String deviceId, int openAngle, int closedAngle) {

    public static final int DEFAULT_OPEN_ANGLE = 238;
    public static final int DEFAULT_CLOSED_ANGLE = 317;

    public static DeviceServoConfig defaultConfig(String deviceId) {
        return new DeviceServoConfig(deviceId, DEFAULT_OPEN_ANGLE, DEFAULT_CLOSED_ANGLE);
    }
}
