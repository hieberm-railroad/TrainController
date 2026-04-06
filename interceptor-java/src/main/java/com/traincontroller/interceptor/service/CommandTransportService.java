package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.metrics.InterceptorTelemetry;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.persistence.CommandAttemptRepository;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import com.traincontroller.interceptor.transport.CommandTransportAdapter;
import com.traincontroller.interceptor.transport.TransportSendResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandTransportService {

    private static final Logger log = LoggerFactory.getLogger(CommandTransportService.class);
    private static final String EVENT_TYPE_LIFECYCLE = "LIFECYCLE";

    private final TcCommandRepository tcCommandRepository;
    private final CommandAttemptRepository commandAttemptRepository;
    private final CommandEventRepository commandEventRepository;
    private final AckIngestionService ackIngestionService;
    private final CommandTransportAdapter commandTransportAdapter;
    private final InterceptorProperties interceptorProperties;
    private final InterceptorTelemetry interceptorTelemetry;

    public CommandTransportService(
            TcCommandRepository tcCommandRepository,
            CommandAttemptRepository commandAttemptRepository,
            CommandEventRepository commandEventRepository,
            AckIngestionService ackIngestionService,
            CommandTransportAdapter commandTransportAdapter,
            InterceptorProperties interceptorProperties,
            InterceptorTelemetry interceptorTelemetry
    ) {
        this.tcCommandRepository = tcCommandRepository;
        this.commandAttemptRepository = commandAttemptRepository;
        this.commandEventRepository = commandEventRepository;
        this.ackIngestionService = ackIngestionService;
        this.commandTransportAdapter = commandTransportAdapter;
        this.interceptorProperties = interceptorProperties;
        this.interceptorTelemetry = interceptorTelemetry;
    }

    @Transactional
    public int sendPending(int batchSize) {
        List<TcCommandEntity> pendingCommands = tcCommandRepository.findByStatus(CommandStatus.SENT, batchSize);

        int sent = 0;
        for (TcCommandEntity command : pendingCommands) {
            int attemptNo = command.retryCount() + 1;
            Instant now = Instant.now();

            try {
                commandAttemptRepository.insertSentAttempt(command.commandId(), attemptNo, now);
            } catch (DataIntegrityViolationException duplicateAttempt) {
                // Attempt already recorded for this command/retry cycle.
                continue;
            }

            Instant sendStartedAt = Instant.now();
            TransportSendResult result = commandTransportAdapter.send(command);
            Duration sendDuration = Duration.between(sendStartedAt, Instant.now());
            if (result.hasTransportError()) {
                interceptorTelemetry.recordTransportResult("transport_error", sendDuration);
                sent += handleTransportError(command, attemptNo, now, result.transportError());
                continue;
            }

            sent++;
            if (result.hasAck()) {
                interceptorTelemetry.recordTransportResult("ack", sendDuration);
                commandAttemptRepository.markAck(command.commandId(), attemptNo, result.ackStatus(), now);
                ackIngestionService.ingestAck(command.commandId(), result.ackStatus());
            } else {
                interceptorTelemetry.recordTransportResult("no_ack", sendDuration);
            }
        }
        return sent;
    }

    private int handleTransportError(TcCommandEntity command, int attemptNo, Instant now, String error) {
        commandAttemptRepository.markTransportError(command.commandId(), attemptNo, error);

        int nextRetryCount = command.retryCount() + 1;
        boolean exhausted = nextRetryCount > command.maxRetries();
        if (exhausted) {
            int failed = tcCommandRepository.updateStatusIfCurrent(
                    command.commandId(),
                    CommandStatus.SENT,
                    CommandStatus.FAILED,
                    "TRANSPORT_ERROR"
            );
            if (failed == 1) {
            interceptorTelemetry.recordTransportFailed();
                commandEventRepository.append(new CommandEventEntity(
                        command.commandId(),
                        command.intentId(),
                        EVENT_TYPE_LIFECYCLE,
                        CommandStatus.FAILED.name(),
                        "{\"transportError\":\"" + error + "\"}"
                ));
            }
            return 0;
        }

        int scheduled = tcCommandRepository.updateRetryStateIfCurrent(
                command.commandId(),
                CommandStatus.SENT,
                CommandStatus.RETRY_SCHEDULED,
                nextRetryCount,
                now.plusMillis(interceptorProperties.retryBackoffMs()),
                "TRANSPORT_ERROR"
        );
        if (scheduled == 1) {
            interceptorTelemetry.recordTransportRetryScheduled();
            commandEventRepository.append(new CommandEventEntity(
                    command.commandId(),
                    command.intentId(),
                    EVENT_TYPE_LIFECYCLE,
                    CommandStatus.RETRY_SCHEDULED.name(),
                    "{\"transportError\":\"" + error + "\"}"
            ));
            log.info("Transport error scheduled retry commandId={} retryCount={} error={}",
                    command.commandId(), nextRetryCount, error);
        }
        return 0;
    }
}
