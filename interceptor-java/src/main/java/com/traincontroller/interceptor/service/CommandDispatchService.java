package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
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
            int updated = tcCommandRepository.updateStatusIfCurrent(
                    command.commandId(),
                    CommandStatus.DISPATCH_READY,
                    CommandStatus.SENT,
                    null
            );
            if (updated != 1) {
                continue;
            }

            commandEventRepository.append(new CommandEventEntity(
                    command.commandId(),
                    command.intentId(),
                    EVENT_TYPE_LIFECYCLE,
                    CommandStatus.SENT.name(),
                    "{}"
            ));
            dispatched++;
            log.info("Marked command as SENT commandId={} intentId={}", command.commandId(), command.intentId());
        }
        return dispatched;
    }
}
