package com.traincontroller.interceptor.persistence;

public interface CommandEventRepository {
    void append(CommandEventEntity event);
}
