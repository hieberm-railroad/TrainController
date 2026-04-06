package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.IntentStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.model.StateQuality;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.DeviceStateEntity;
import com.traincontroller.interceptor.persistence.DeviceStateRepository;
import com.traincontroller.interceptor.persistence.IntentEntity;
import com.traincontroller.interceptor.persistence.IntentRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntentService {

    private static final String SOURCE_SYSTEM_JMRI = "JMRI";
    private static final String EVENT_TYPE_LIFECYCLE = "LIFECYCLE";

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);

    private final IntentRepository intentRepository;
    private final TcCommandRepository tcCommandRepository;
    private final CommandEventRepository commandEventRepository;
    private final DeviceStateRepository deviceStateRepository;
    private final InterceptorProperties interceptorProperties;

    public IntentService(
            IntentRepository intentRepository,
            TcCommandRepository tcCommandRepository,
            CommandEventRepository commandEventRepository,
            DeviceStateRepository deviceStateRepository,
            InterceptorProperties interceptorProperties
    ) {
        this.intentRepository = intentRepository;
        this.tcCommandRepository = tcCommandRepository;
        this.commandEventRepository = commandEventRepository;
        this.deviceStateRepository = deviceStateRepository;
        this.interceptorProperties = interceptorProperties;
    }

    @Transactional
    public void handle(TurnoutIntent intent) {
        Instant now = intent.createdAt() == null ? Instant.now() : intent.createdAt();
        String candidateIntentId = UUID.randomUUID().toString();
        String deviceId = intent.turnoutId();
        String desiredState = intent.desiredState().name();

        String intentId = intentRepository.upsert(new IntentEntity(
                candidateIntentId,
                intent.correlationId(),
                SOURCE_SYSTEM_JMRI,
                deviceId,
                OperationType.TURNOUT_SET,
                desiredState,
                now,
                now,
                IntentStatus.RECEIVED,
                null
        ));

        try {
            tcCommandRepository.insert(new TcCommandEntity(
                    intent.commandId(),
                    intentId,
                    intent.correlationId(),
                    deviceId,
                    deriveNodeId(intent),
                    OperationType.TURNOUT_SET,
                    desiredState,
                    CommandStatus.RECEIVED,
                    0,
                    interceptorProperties.maxRetries(),
                    interceptorProperties.settleDelayMs(),
                    null,
                    null
            ));
        } catch (DataIntegrityViolationException e) {
            log.info("Ignoring duplicate commandId={} correlationId={}", intent.commandId(), intent.correlationId());
            return;
        }

        appendLifecycleEvent(intent.commandId(), intentId, CommandStatus.RECEIVED);
        tcCommandRepository.updateStatus(intent.commandId(), CommandStatus.PERSISTED, null);
        appendLifecycleEvent(intent.commandId(), intentId, CommandStatus.PERSISTED);
        tcCommandRepository.updateStatus(intent.commandId(), CommandStatus.DISPATCH_READY, null);
        appendLifecycleEvent(intent.commandId(), intentId, CommandStatus.DISPATCH_READY);

        deviceStateRepository.upsert(new DeviceStateEntity(
                deviceId,
                desiredState,
                null,
                intent.commandId(),
                StateQuality.UNKNOWN,
                null
        ));

        log.info("Received intent commandId={} turnoutId={} desiredState={}",
                intent.commandId(), intent.turnoutId(), intent.desiredState());
    }

    private void appendLifecycleEvent(String commandId, String intentId, CommandStatus status) {
        commandEventRepository.append(new CommandEventEntity(
                commandId,
                intentId,
                EVENT_TYPE_LIFECYCLE,
                status.name(),
                "{}"
        ));
    }

    private static String deriveNodeId(TurnoutIntent intent) {
        return intent.turnoutId();
    }
}
