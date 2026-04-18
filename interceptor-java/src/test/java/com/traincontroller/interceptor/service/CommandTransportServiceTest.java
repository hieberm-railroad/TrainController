package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.metrics.InterceptorTelemetry;
import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.persistence.CommandAttemptRepository;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import com.traincontroller.interceptor.transport.CommandTransportAdapter;
import com.traincontroller.interceptor.transport.TransportSendResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandTransportServiceTest {

    @Mock
    private TcCommandRepository tcCommandRepository;

    @Mock
    private CommandAttemptRepository commandAttemptRepository;

    @Mock
    private CommandEventRepository commandEventRepository;

    @Mock
    private AckIngestionService ackIngestionService;

    @Mock
    private CommandTransportAdapter commandTransportAdapter;

    @Mock
    private InterceptorTelemetry interceptorTelemetry;

    @Test
    void sendPendingRecordsAttemptAndForwardsAck() {
        CommandTransportService service = new CommandTransportService(
                tcCommandRepository,
                commandAttemptRepository,
                commandEventRepository,
                ackIngestionService,
                commandTransportAdapter,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200, null),
                interceptorTelemetry
        );

        TcCommandEntity command = command("cmd-t-1", "intent-t-1", 0, 5);
        when(tcCommandRepository.findByStatus(CommandStatus.SENT, 10)).thenReturn(List.of(command));
        when(commandTransportAdapter.send(command)).thenReturn(TransportSendResult.ack(AckStatus.ACCEPTED));

        int sent = service.sendPending(10);

        assertEquals(1, sent);
        verify(commandAttemptRepository, times(1)).insertSentAttempt(any(), anyInt(), any());
        verify(commandAttemptRepository, times(1)).markAck(any(), anyInt(), any(), any());
        verify(ackIngestionService, times(1)).ingestAck("cmd-t-1", AckStatus.ACCEPTED);
    }

    @Test
    void sendPendingTransportErrorSchedulesRetry() {
        CommandTransportService service = new CommandTransportService(
                tcCommandRepository,
                commandAttemptRepository,
                commandEventRepository,
                ackIngestionService,
                commandTransportAdapter,
                new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200, null),
                interceptorTelemetry
        );

        TcCommandEntity command = command("cmd-t-2", "intent-t-2", 0, 5);
        when(tcCommandRepository.findByStatus(CommandStatus.SENT, 10)).thenReturn(List.of(command));
        when(commandTransportAdapter.send(command)).thenReturn(TransportSendResult.transportError("timeout"));
        when(tcCommandRepository.updateRetryStateIfCurrent(any(), any(), any(), anyInt(), any(), any())).thenReturn(1);

        int sent = service.sendPending(10);

        assertEquals(0, sent);
        verify(commandAttemptRepository, times(1)).markTransportError(any(), anyInt(), any());
        verify(tcCommandRepository, times(1)).updateRetryStateIfCurrent(any(), any(), any(), anyInt(), any(), any());
        verify(commandEventRepository, times(1)).append(any(CommandEventEntity.class));
        verify(ackIngestionService, never()).ingestAck(any(), any());
    }

    private static TcCommandEntity command(String commandId, String intentId, int retryCount, int maxRetries) {
        return new TcCommandEntity(
                commandId,
                intentId,
                "corr-1",
                "turnout-12",
                "turnout-12",
                OperationType.TURNOUT_SET,
                "OPEN",
                CommandStatus.SENT,
                retryCount,
                maxRetries,
                750,
                null,
                null
        );
    }
}
