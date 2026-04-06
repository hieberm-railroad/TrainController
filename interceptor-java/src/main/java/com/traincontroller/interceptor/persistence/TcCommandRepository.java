package com.traincontroller.interceptor.persistence;

import com.traincontroller.interceptor.model.CommandStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TcCommandRepository {
    void insert(TcCommandEntity command);

    int updateStatus(String commandId, CommandStatus status, String failureReason);

    List<TcCommandEntity> findByStatus(CommandStatus status, int limit);

    List<TcCommandEntity> findRetryScheduledDue(Instant now, int limit);

    Optional<TcCommandEntity> findByCommandId(String commandId);

    Optional<Instant> findCreatedAtByCommandId(String commandId);

    int updateStatusIfCurrent(
            String commandId,
            CommandStatus expectedStatus,
            CommandStatus newStatus,
            String failureReason
    );

        int updateRetryStateIfCurrent(
            String commandId,
            CommandStatus expectedStatus,
            CommandStatus newStatus,
            int retryCount,
            Instant nextAttemptAt,
            String failureReason
        );
}
