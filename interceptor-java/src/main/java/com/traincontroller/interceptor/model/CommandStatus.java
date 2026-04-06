package com.traincontroller.interceptor.model;

public enum CommandStatus {
    RECEIVED,
    PERSISTED,
    DISPATCH_READY,
    SENT,
    ACKED,
    VERIFY_PENDING,
    VERIFIED,
    RETRY_SCHEDULED,
    FAILED,
    CANCELLED
}
