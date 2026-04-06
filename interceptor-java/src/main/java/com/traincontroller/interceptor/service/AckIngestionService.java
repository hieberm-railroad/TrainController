package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AckIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AckIngestionService.class);

    private static final String EVENT_TYPE_LIFECYCLE = "LIFECYCLE";

    private final TcCommandRepository tcCommandRepository;
    private final CommandEventRepository commandEventRepository;

    public AckIngestionService(
            TcCommandRepository tcCommandRepository,
            CommandEventRepository commandEventRepository
    ) {
        this.tcCommandRepository = tcCommandRepository;
        this.commandEventRepository = commandEventRepository;
    }

    @Transactional
    public boolean ingestAck(String commandId, AckStatus ackStatus) {
        Optional<TcCommandEntity> command = tcCommandRepository.findByCommandId(commandId);
        if (command.isEmpty()) {
            log.info("Ignoring ACK for unknown commandId={} ackStatus={}", commandId, ackStatus);
            return false;
        }

        CommandStatus targetStatus = toTargetStatus(ackStatus);
        String failureReason = targetStatus == CommandStatus.FAILED ? "ACK_" + ackStatus.name() : null;

        int updated = tcCommandRepository.updateStatusIfCurrent(
                commandId,
                CommandStatus.SENT,
                targetStatus,
                failureReason
        );
        if (updated != 1) {
            log.info("Ignoring ACK for non-SENT commandId={} ackStatus={}", commandId, ackStatus);
            return false;
        }

        commandEventRepository.append(new CommandEventEntity(
                commandId,
                command.get().intentId(),
                EVENT_TYPE_LIFECYCLE,
                targetStatus.name(),
                "{\"ackStatus\":\"" + ackStatus.name() + "\"}"
        ));
        log.info("Processed ACK commandId={} ackStatus={} targetStatus={}", commandId, ackStatus, targetStatus);
        return true;
    }

    private static CommandStatus toTargetStatus(AckStatus ackStatus) {
        return switch (ackStatus) {
            case ACCEPTED, DUPLICATE -> CommandStatus.ACKED;
            case REJECTED, STALE -> CommandStatus.FAILED;
        };
    }
}
