package com.traincontroller.interceptor.persistence;

public record CommandEventEntity(
        String commandId,
        String intentId,
        String eventType,
        String eventStatus,
        String detailJson
) {
}
