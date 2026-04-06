package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AckIngestionServiceTest {

    @Mock
    private TcCommandRepository tcCommandRepository;

    @Mock
    private CommandEventRepository commandEventRepository;

    @Test
    void ingestAcceptedAckTransitionsSentToAcked() {
        AckIngestionService service = new AckIngestionService(tcCommandRepository, commandEventRepository);

        when(tcCommandRepository.findByCommandId("cmd-1")).thenReturn(Optional.of(command("cmd-1", "intent-1")));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-1", CommandStatus.SENT, CommandStatus.ACKED, null))
                .thenReturn(1);

        boolean processed = service.ingestAck("cmd-1", AckStatus.ACCEPTED);

        assertTrue(processed);
        verify(tcCommandRepository, times(1))
                .updateStatusIfCurrent("cmd-1", CommandStatus.SENT, CommandStatus.ACKED, null);

        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, times(1)).append(eventCaptor.capture());
        assertEquals("ACKED", eventCaptor.getValue().eventStatus());
    }

    @Test
    void ingestRejectedAckTransitionsSentToFailed() {
        AckIngestionService service = new AckIngestionService(tcCommandRepository, commandEventRepository);

        when(tcCommandRepository.findByCommandId("cmd-2")).thenReturn(Optional.of(command("cmd-2", "intent-2")));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-2", CommandStatus.SENT, CommandStatus.FAILED, "ACK_REJECTED"))
                .thenReturn(1);

        boolean processed = service.ingestAck("cmd-2", AckStatus.REJECTED);

        assertTrue(processed);
        verify(tcCommandRepository, times(1))
                .updateStatusIfCurrent("cmd-2", CommandStatus.SENT, CommandStatus.FAILED, "ACK_REJECTED");

        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, times(1)).append(eventCaptor.capture());
        assertEquals("FAILED", eventCaptor.getValue().eventStatus());
    }

    @Test
    void ingestAckForUnknownCommandIsIgnored() {
        AckIngestionService service = new AckIngestionService(tcCommandRepository, commandEventRepository);

        when(tcCommandRepository.findByCommandId("missing")).thenReturn(Optional.empty());

        boolean processed = service.ingestAck("missing", AckStatus.ACCEPTED);

        assertFalse(processed);
        verify(tcCommandRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
        verify(commandEventRepository, never()).append(any());
    }

    @Test
    void ingestAckForNonSentCommandIsIgnored() {
        AckIngestionService service = new AckIngestionService(tcCommandRepository, commandEventRepository);

        when(tcCommandRepository.findByCommandId("cmd-3")).thenReturn(Optional.of(command("cmd-3", "intent-3")));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-3", CommandStatus.SENT, CommandStatus.ACKED, null))
                .thenReturn(0);

        boolean processed = service.ingestAck("cmd-3", AckStatus.DUPLICATE);

        assertFalse(processed);
        verify(commandEventRepository, never()).append(any());
    }

    private static TcCommandEntity command(String commandId, String intentId) {
        return new TcCommandEntity(
                commandId,
                intentId,
                "corr-1",
                "turnout-12",
                "turnout-12",
                OperationType.TURNOUT_SET,
                "OPEN",
                CommandStatus.SENT,
                0,
                5,
                750,
                null,
                null
        );
    }
}
