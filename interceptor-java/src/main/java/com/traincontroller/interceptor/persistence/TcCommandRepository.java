package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.CommandStatus;
import java.util.List;

public interface TcCommandRepository {
    void insert(TcCommandEntity command);

    int updateStatus(String commandId, CommandStatus status, String failureReason);

    List<TcCommandEntity> findByStatus(CommandStatus status, int limit);

    int updateStatusIfCurrent(
            String commandId,
            CommandStatus expectedStatus,
            CommandStatus newStatus,
            String failureReason
    );
}
