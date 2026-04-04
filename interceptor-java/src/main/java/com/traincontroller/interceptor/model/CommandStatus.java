package com.traincontroller.interceptor.model;

public enum CommandStatus {
    PENDING,
    DISPATCHED,
    ACKED,
    VERIFYING,
    VERIFIED,
    RETRYING,
    FAILED
}
