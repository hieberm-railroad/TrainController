package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandDispatchService {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatchService.class);

    private static final String EVENT_TYPE_LIFECYCLE = "LIFECYCLE";

    private final TcCommandRepository tcCommandRepository;
    private final CommandEventRepository commandEventRepository;

    public CommandDispatchService(
            TcCommandRepository tcCommandRepository,
            CommandEventRepository commandEventRepository
    ) {
        this.tcCommandRepository = tcCommandRepository;
        this.commandEventRepository = commandEventRepository;
    }

    @Transactional
    public int dispatchReady(int batchSize) {
        List<TcCommandEntity> readyCommands = tcCommandRepository.findByStatus(CommandStatus.DISPATCH_READY, batchSize);

        int dispatched = 0;
        for (TcCommandEntity command : readyCommands) {
            dispatched += markSent(command, CommandStatus.DISPATCH_READY);
        }
        return dispatched;
    }

    @Transactional
    public int dispatchRetriesDue(Instant now, int batchSize) {
        List<TcCommandEntity> dueRetryCommands = tcCommandRepository.findRetryScheduledDue(now, batchSize);

        int dispatched = 0;
        for (TcCommandEntity command : dueRetryCommands) {
            dispatched += markSent(command, CommandStatus.RETRY_SCHEDULED);
        }
        return dispatched;
    }

    private int markSent(TcCommandEntity command, CommandStatus expectedStatus) {
        int updated = tcCommandRepository.updateStatusIfCurrent(
                command.commandId(),
                expectedStatus,
                CommandStatus.SENT,
                null
        );
        if (updated != 1) {
            return 0;
        }

        commandEventRepository.append(new CommandEventEntity(
                command.commandId(),
                command.intentId(),
                EVENT_TYPE_LIFECYCLE,
                CommandStatus.SENT.name(),
                "{}"
        ));
        log.info("Marked command as SENT commandId={} intentId={} fromStatus={}",
                command.commandId(), command.intentId(), expectedStatus);
        return 1;
    }
}
