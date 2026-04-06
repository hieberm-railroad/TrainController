package com.traincontroller.interceptor.transport;

import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.model.OperationType;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SerialTurnoutStateReadbackAdapterTest {

    @Mock
    private SerialExchangeClient serialExchangeClient;

    @Test
    void readActualStateReturnsParsedState() throws Exception {
        SerialTurnoutStateReadbackAdapter adapter = new SerialTurnoutStateReadbackAdapter(serialExchangeClient);
        when(serialExchangeClient.exchange(any(), anyInt())).thenReturn("STATE|OPEN\n");

        Optional<String> actualState = adapter.readActualState(command("cmd-r-1"));

        assertTrue(actualState.isPresent());
        assertEquals("OPEN", actualState.get());
    }

    @Test
    void readActualStateReturnsEmptyOnIoError() throws Exception {
        SerialTurnoutStateReadbackAdapter adapter = new SerialTurnoutStateReadbackAdapter(serialExchangeClient);
        when(serialExchangeClient.exchange(any(), anyInt())).thenThrow(new IOException("read timeout"));

        Optional<String> actualState = adapter.readActualState(command("cmd-r-2"));

        assertTrue(actualState.isEmpty());
    }

    private static TcCommandEntity command(String commandId) {
        return new TcCommandEntity(
                commandId,
                "intent-1",
                "corr-1",
                "turnout-12",
                "turnout-12",
                OperationType.TURNOUT_SET,
                "OPEN",
                CommandStatus.ACKED,
                0,
                5,
                750,
                null,
                null
        );
    }
}