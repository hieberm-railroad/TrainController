package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandDispatchServiceTest {

    @Mock
    private TcCommandRepository tcCommandRepository;

    @Mock
    private CommandEventRepository commandEventRepository;

    @Test
    void dispatchReadyMarksSentAndAppendsEvent() {
        CommandDispatchService service = new CommandDispatchService(tcCommandRepository, commandEventRepository);

        TcCommandEntity first = command("cmd-1", "intent-1");
        TcCommandEntity second = command("cmd-2", "intent-2");

        when(tcCommandRepository.findByStatus(CommandStatus.DISPATCH_READY, 10)).thenReturn(List.of(first, second));
        when(tcCommandRepository.updateStatusIfCurrent("cmd-1", CommandStatus.DISPATCH_READY, CommandStatus.SENT, null))
                .thenReturn(1);
        when(tcCommandRepository.updateStatusIfCurrent("cmd-2", CommandStatus.DISPATCH_READY, CommandStatus.SENT, null))
                .thenReturn(0);

        int dispatched = service.dispatchReady(10);

        assertEquals(1, dispatched);
        verify(tcCommandRepository, times(1))
                .findByStatus(CommandStatus.DISPATCH_READY, 10);

        ArgumentCaptor<CommandEventEntity> eventCaptor = ArgumentCaptor.forClass(CommandEventEntity.class);
        verify(commandEventRepository, times(1)).append(eventCaptor.capture());

        CommandEventEntity event = eventCaptor.getValue();
        assertEquals("cmd-1", event.commandId());
        assertEquals("intent-1", event.intentId());
        assertEquals("LIFECYCLE", event.eventType());
        assertEquals("SENT", event.eventStatus());
    }

    @Test
    void dispatchReadyNoCandidatesDoesNothing() {
        CommandDispatchService service = new CommandDispatchService(tcCommandRepository, commandEventRepository);

        when(tcCommandRepository.findByStatus(CommandStatus.DISPATCH_READY, 5)).thenReturn(List.of());

        int dispatched = service.dispatchReady(5);

        assertEquals(0, dispatched);
        verify(tcCommandRepository, times(1)).findByStatus(CommandStatus.DISPATCH_READY, 5);
        verify(tcCommandRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
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
                CommandStatus.DISPATCH_READY,
                0,
                5,
                750,
                null,
                null
        );
    }
}
