package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.CommandStatus;

public interface TcCommandRepository {
    void insert(TcCommandEntity command);

    int updateStatus(String commandId, CommandStatus status, String failureReason);
}
