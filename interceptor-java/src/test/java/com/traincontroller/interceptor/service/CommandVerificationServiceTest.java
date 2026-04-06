package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.metrics.InterceptorTelemetry;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.model.StateQuality;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.DeviceStateEntity;
import com.traincontroller.interceptor.persistence.DeviceStateRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import com.traincontroller.interceptor.transport.TurnoutStateReadbackAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandVerificationServiceTest {

    @Mock
    private TcCommandRepository tcCommandRepository;

    @Mock
    private DeviceStateRepository deviceStateRepository;

    @Mock
    private CommandEventRepository commandEventRepository;

        @Mock
        private TurnoutStateReadbackAdapter turnoutStateReadbackAdapter;

        @Mock
        private InterceptorTelemetry interceptorTelemetry;

    @Test
    void reconcileAckedMatchingStateTransitionsToVerified() {
        CommandVerificationService service = new CommandVerificationService(
                tcCommandRepository,
                deviceStateRepository,
                commandEventRepository,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                turnoutStateReadbackAdapter,
                interceptorTelemetry
        );

        TcCommandEntity command = command("cmd-v-1", "intent-v-1", "OPEN", 0, 5, CommandStatus.ACKED);
        Instant observedAt = Instant.parse("2026-04-06T14:00:00Z");

        when(tcCommandRepository.findByCommandId("cmd-v-1")).thenReturn(Optional.of(command));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-1", CommandStatus.ACKED, CommandStatus.VERIFY_PENDING, null))
                .thenReturn(1);
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-1", CommandStatus.VERIFY_PENDING, CommandStatus.VERIFIED, null))
                .thenReturn(1);

        boolean processed = service.reconcileAcked("cmd-v-1", "OPEN", observedAt);

        assertTrue(processed);
        ArgumentCaptor<DeviceStateEntity> stateCaptor = ArgumentCaptor.forClass(DeviceStateEntity.class);
        verify(deviceStateRepository, times(1)).upsert(stateCaptor.capture());
        assertEquals(StateQuality.GOOD, stateCaptor.getValue().stateQuality());

        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, times(1)).append(eventCaptor.capture());
        assertEquals(CommandStatus.VERIFIED.name(), eventCaptor.getValue().eventStatus());
    }

    @Test
    void reconcileAckedMismatchedStateSchedulesRetry() {
        CommandVerificationService service = new CommandVerificationService(
                tcCommandRepository,
                deviceStateRepository,
                commandEventRepository,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                turnoutStateReadbackAdapter,
                interceptorTelemetry
        );

        TcCommandEntity command = command("cmd-v-2", "intent-v-2", "OPEN", 0, 5, CommandStatus.ACKED);
        Instant observedAt = Instant.parse("2026-04-06T14:01:00Z");

        when(tcCommandRepository.findByCommandId("cmd-v-2")).thenReturn(Optional.of(command));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-2", CommandStatus.ACKED, CommandStatus.VERIFY_PENDING, null))
                .thenReturn(1);
        when(tcCommandRepository.updateRetryStateIfCurrent(
                "cmd-v-2",
                CommandStatus.VERIFY_PENDING,
                CommandStatus.RETRY_SCHEDULED,
                1,
                observedAt.plusMillis(500),
                "VERIFY_MISMATCH"
        )).thenReturn(1);

        boolean processed = service.reconcileAcked("cmd-v-2", "CLOSED", observedAt);

        assertTrue(processed);
        ArgumentCaptor<DeviceStateEntity> stateCaptor = ArgumentCaptor.forClass(DeviceStateEntity.class);
        verify(deviceStateRepository, times(1)).upsert(stateCaptor.capture());
        assertEquals(StateQuality.DEGRADED, stateCaptor.getValue().stateQuality());

        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, times(1)).append(eventCaptor.capture());
        assertEquals(CommandStatus.RETRY_SCHEDULED.name(), eventCaptor.getValue().eventStatus());
    }

    @Test
    void reconcileAckedMismatchedStateFailsWhenRetriesExhausted() {
        CommandVerificationService service = new CommandVerificationService(
                tcCommandRepository,
                deviceStateRepository,
                commandEventRepository,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                turnoutStateReadbackAdapter,
                interceptorTelemetry
        );

        TcCommandEntity command = command("cmd-v-3", "intent-v-3", "OPEN", 5, 5, CommandStatus.ACKED);
        Instant observedAt = Instant.parse("2026-04-06T14:02:00Z");

        when(tcCommandRepository.findByCommandId("cmd-v-3")).thenReturn(Optional.of(command));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-3", CommandStatus.ACKED, CommandStatus.VERIFY_PENDING, null))
                .thenReturn(1);
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-3", CommandStatus.VERIFY_PENDING, CommandStatus.FAILED, "VERIFY_MISMATCH"))
                .thenReturn(1);

        boolean processed = service.reconcileAcked("cmd-v-3", "CLOSED", observedAt);

        assertTrue(processed);
        verify(tcCommandRepository, never()).updateRetryStateIfCurrent(any(), any(), any(), anyInt(), any(), any());
        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, times(1)).append(eventCaptor.capture());
        assertEquals(CommandStatus.FAILED.name(), eventCaptor.getValue().eventStatus());
    }

    @Test
    void reconcileAckedIgnoresUnknownOrNonAcked() {
        CommandVerificationService service = new CommandVerificationService(
                tcCommandRepository,
                deviceStateRepository,
                commandEventRepository,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                turnoutStateReadbackAdapter,
                interceptorTelemetry
        );

        when(tcCommandRepository.findByCommandId("missing")).thenReturn(Optional.empty());
        assertFalse(service.reconcileAcked("missing", "OPEN", Instant.parse("2026-04-06T14:03:00Z")));

        TcCommandEntity notAcked = command("cmd-v-4", "intent-v-4", "OPEN", 0, 5, CommandStatus.SENT);
        when(tcCommandRepository.findByCommandId("cmd-v-4")).thenReturn(Optional.of(notAcked));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-4", CommandStatus.ACKED, CommandStatus.VERIFY_PENDING, null))
                .thenReturn(0);

        assertFalse(service.reconcileAcked("cmd-v-4", "OPEN", Instant.parse("2026-04-06T14:03:10Z")));
        verify(commandEventRepository, never()).append(any());
    }

    @Test
    void pollAndReconcileAckedProcessesOnlyCommandsWithReadbackState() {
        CommandVerificationService service = new CommandVerificationService(
                tcCommandRepository,
                deviceStateRepository,
                commandEventRepository,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                turnoutStateReadbackAdapter,
                interceptorTelemetry
        );

        TcCommandEntity cmd1 = command("cmd-v-5", "intent-v-5", "OPEN", 0, 5, CommandStatus.ACKED);
        TcCommandEntity cmd2 = command("cmd-v-6", "intent-v-6", "OPEN", 0, 5, CommandStatus.ACKED);

        when(tcCommandRepository.findByStatus(CommandStatus.ACKED, 10)).thenReturn(List.of(cmd1, cmd2));
        when(turnoutStateReadbackAdapter.readActualState(cmd1)).thenReturn(Optional.of("OPEN"));
        when(turnoutStateReadbackAdapter.readActualState(cmd2)).thenReturn(Optional.empty());

        when(tcCommandRepository.findByCommandId("cmd-v-5")).thenReturn(Optional.of(cmd1));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-5", CommandStatus.ACKED, CommandStatus.VERIFY_PENDING, null))
                .thenReturn(1);
        when(tcCommandRepository.updateStatusIfCurrent("cmd-v-5", CommandStatus.VERIFY_PENDING, CommandStatus.VERIFIED, null))
                .thenReturn(1);

        int reconciled = service.pollAndReconcileAcked(10);

        assertEquals(1, reconciled);
    }

    private static TcCommandEntity command(
            String commandId,
            String intentId,
            String desiredState,
            int retryCount,
            int maxRetries,
            CommandStatus status
    ) {
        return new TcCommandEntity(
                commandId,
                intentId,
                "corr-1",
                "turnout-12",
                "turnout-12",
                OperationType.TURNOUT_SET,
                desiredState,
                status,
                retryCount,
                maxRetries,
                750,
                null,
                null
        );
    }
}
