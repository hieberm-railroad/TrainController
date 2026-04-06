package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.metrics.InterceptorTelemetry;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.StateQuality;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.DeviceStateEntity;
import com.traincontroller.interceptor.persistence.DeviceStateRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import com.traincontroller.interceptor.transport.TurnoutStateReadbackAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CommandVerificationService.class);

    private static final String EVENT_TYPE_LIFECYCLE = "LIFECYCLE";

    private final TcCommandRepository tcCommandRepository;
    private final DeviceStateRepository deviceStateRepository;
    private final CommandEventRepository commandEventRepository;
    private final InterceptorProperties interceptorProperties;
    private final TurnoutStateReadbackAdapter turnoutStateReadbackAdapter;
    private final InterceptorTelemetry interceptorTelemetry;

    public CommandVerificationService(
            TcCommandRepository tcCommandRepository,
            DeviceStateRepository deviceStateRepository,
            CommandEventRepository commandEventRepository,
            InterceptorProperties interceptorProperties,
            TurnoutStateReadbackAdapter turnoutStateReadbackAdapter,
            InterceptorTelemetry interceptorTelemetry
    ) {
        this.tcCommandRepository = tcCommandRepository;
        this.deviceStateRepository = deviceStateRepository;
        this.commandEventRepository = commandEventRepository;
        this.interceptorProperties = interceptorProperties;
        this.turnoutStateReadbackAdapter = turnoutStateReadbackAdapter;
        this.interceptorTelemetry = interceptorTelemetry;
    }

    @Transactional
    public int pollAndReconcileAcked(int batchSize) {
        List<TcCommandEntity> ackedCommands = tcCommandRepository.findByStatus(CommandStatus.ACKED, batchSize);

        int reconciled = 0;
        for (TcCommandEntity command : ackedCommands) {
            Optional<String> actualState = turnoutStateReadbackAdapter.readActualState(command);
            if (actualState.isEmpty()) {
                continue;
            }

            if (reconcileAcked(command.commandId(), actualState.get(), Instant.now())) {
                reconciled++;
            }
        }
        return reconciled;
    }

    @Transactional
    public boolean reconcileAcked(String commandId, String actualState, Instant observedAt) {
        Optional<TcCommandEntity> command = tcCommandRepository.findByCommandId(commandId);
        if (command.isEmpty()) {
            interceptorTelemetry.recordVerificationOutcome("ignored_unknown");
            log.info("Ignoring verify for unknown commandId={}", commandId);
            return false;
        }

        int movedToVerifyPending = tcCommandRepository.updateStatusIfCurrent(
                commandId,
                CommandStatus.ACKED,
                CommandStatus.VERIFY_PENDING,
                null
        );
        if (movedToVerifyPending != 1) {
            interceptorTelemetry.recordVerificationOutcome("ignored_non_acked");
            log.info("Ignoring verify for non-ACKED commandId={}", commandId);
            return false;
        }

        TcCommandEntity tcCommand = command.get();
        boolean matches = tcCommand.desiredState().equalsIgnoreCase(actualState);
        if (matches) {
            return completeVerified(tcCommand, actualState, observedAt);
        }
        return scheduleRetryOrFail(tcCommand, actualState, observedAt);
    }

    private boolean completeVerified(TcCommandEntity command, String actualState, Instant observedAt) {
        int updated = tcCommandRepository.updateStatusIfCurrent(
                command.commandId(),
                CommandStatus.VERIFY_PENDING,
                CommandStatus.VERIFIED,
                null
        );
        if (updated != 1) {
            return false;
        }

        deviceStateRepository.upsert(new DeviceStateEntity(
                command.deviceId(),
                command.desiredState(),
                actualState,
                command.commandId(),
                StateQuality.GOOD,
                observedAt
        ));
        commandEventRepository.append(new CommandEventEntity(
                command.commandId(),
                command.intentId(),
                EVENT_TYPE_LIFECYCLE,
                CommandStatus.VERIFIED.name(),
                "{\"actualState\":\"" + actualState + "\"}"
        ));
        interceptorTelemetry.recordVerificationOutcome("verified");
        recordLifecycleLatency(command.commandId(), "verified", observedAt);
        return true;
    }

    private boolean scheduleRetryOrFail(TcCommandEntity command, String actualState, Instant observedAt) {
        int nextRetryCount = command.retryCount() + 1;
        boolean exhausted = nextRetryCount > command.maxRetries();

        int updated;
        CommandStatus finalStatus;
        if (exhausted) {
            finalStatus = CommandStatus.FAILED;
            updated = tcCommandRepository.updateStatusIfCurrent(
                    command.commandId(),
                    CommandStatus.VERIFY_PENDING,
                    finalStatus,
                    "VERIFY_MISMATCH"
            );
        } else {
            finalStatus = CommandStatus.RETRY_SCHEDULED;
            Instant nextAttemptAt = observedAt.plusMillis(interceptorProperties.retryBackoffMs());
            updated = tcCommandRepository.updateRetryStateIfCurrent(
                    command.commandId(),
                    CommandStatus.VERIFY_PENDING,
                    finalStatus,
                    nextRetryCount,
                    nextAttemptAt,
                    "VERIFY_MISMATCH"
            );
        }

        if (updated != 1) {
            return false;
        }

        deviceStateRepository.upsert(new DeviceStateEntity(
                command.deviceId(),
                command.desiredState(),
                actualState,
                command.commandId(),
                StateQuality.DEGRADED,
                observedAt
        ));
        commandEventRepository.append(new CommandEventEntity(
                command.commandId(),
                command.intentId(),
                EVENT_TYPE_LIFECYCLE,
                finalStatus.name(),
                "{\"actualState\":\"" + actualState + "\"}"
        ));
        String outcome = finalStatus == CommandStatus.FAILED ? "failed" : "retry_scheduled";
        interceptorTelemetry.recordVerificationOutcome(outcome);
        if (finalStatus == CommandStatus.FAILED) {
            recordLifecycleLatency(command.commandId(), "failed", observedAt);
        }
        return true;
    }

    private void recordLifecycleLatency(String commandId, String outcome, Instant observedAt) {
        tcCommandRepository.findCreatedAtByCommandId(commandId)
                .filter(createdAt -> !observedAt.isBefore(createdAt))
                .ifPresent(createdAt -> interceptorTelemetry.recordCommandLifecycleLatency(
                        outcome,
                        Duration.between(createdAt, observedAt)
                ));
    }
}
