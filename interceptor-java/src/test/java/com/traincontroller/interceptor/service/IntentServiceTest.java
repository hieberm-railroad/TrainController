package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.model.StateQuality;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.DeviceStateEntity;
import com.traincontroller.interceptor.persistence.DeviceStateRepository;
import com.traincontroller.interceptor.persistence.IntentEntity;
import com.traincontroller.interceptor.persistence.IntentRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IntentServiceTest {

    @Mock
    private IntentRepository intentRepository;

    @Mock
    private TcCommandRepository tcCommandRepository;

    @Mock
    private CommandEventRepository commandEventRepository;

    @Mock
    private DeviceStateRepository deviceStateRepository;

    @Test
    void handlePersistsLifecycleInExpectedOrder() {
        IntentService service = new IntentService(
                intentRepository,
                tcCommandRepository,
                commandEventRepository,
                deviceStateRepository,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
        );

        TurnoutIntent intent = new TurnoutIntent(
                "cmd-100",
                "corr-100",
                "turnout-12",
                TurnoutState.OPEN,
                Instant.parse("2026-04-06T12:00:00Z")
        );

        service.handle(intent);

        ArgumentCaptor<IntentEntity> intentCaptor = ArgumentCaptor.forClass(IntentEntity.class);
        verify(intentRepository).insert(intentCaptor.capture());

        IntentEntity persistedIntent = intentCaptor.getValue();
        assertNotNull(persistedIntent.intentId());
        assertEquals("corr-100", persistedIntent.correlationId());
        assertEquals("JMRI", persistedIntent.sourceSystem());
        assertEquals("turnout-12", persistedIntent.deviceId());
        assertEquals(OperationType.TURNOUT_SET, persistedIntent.operationType());
        assertEquals("OPEN", persistedIntent.desiredState());

        ArgumentCaptor<TcCommandEntity> commandCaptor = ArgumentCaptor.forClass(TcCommandEntity.class);
        verify(tcCommandRepository).insert(commandCaptor.capture());

        TcCommandEntity command = commandCaptor.getValue();
        assertEquals("cmd-100", command.commandId());
        assertEquals(persistedIntent.intentId(), command.intentId());
        assertEquals("corr-100", command.correlationId());
        assertEquals("turnout-12", command.deviceId());
        assertEquals("turnout-12", command.nodeId());
        assertEquals(OperationType.TURNOUT_SET, command.operationType());
        assertEquals("OPEN", command.desiredState());
        assertEquals(CommandStatus.RECEIVED, command.commandStatus());
        assertEquals(5, command.maxRetries());
        assertEquals(750, command.settleDelayMs());

        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, org.mockito.Mockito.times(3)).append(eventCaptor.capture());
        List<CommandEventEntity> events = eventCaptor.getAllValues();
        assertEquals(CommandStatus.RECEIVED.name(), events.get(0).eventStatus());
        assertEquals(CommandStatus.PERSISTED.name(), events.get(1).eventStatus());
        assertEquals(CommandStatus.DISPATCH_READY.name(), events.get(2).eventStatus());

        InOrder inOrder = inOrder(tcCommandRepository);
        inOrder.verify(tcCommandRepository).updateStatus("cmd-100", CommandStatus.PERSISTED, null);
        inOrder.verify(tcCommandRepository).updateStatus("cmd-100", CommandStatus.DISPATCH_READY, null);

        ArgumentCaptor<DeviceStateEntity> stateCaptor = ArgumentCaptor.forClass(DeviceStateEntity.class);
        verify(deviceStateRepository).upsert(stateCaptor.capture());
        DeviceStateEntity state = stateCaptor.getValue();
        assertEquals("turnout-12", state.deviceId());
        assertEquals("OPEN", state.desiredState());
        assertEquals("cmd-100", state.lastCommandId());
        assertEquals(StateQuality.UNKNOWN, state.stateQuality());
    }
}
