package com.traincontroller.interceptor.service;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifecycleSchedulerTest {

    @Mock
    private CommandDispatchService commandDispatchService;

    @Test
    void runCycleInvokesReadyAndRetryDispatch() {
        LifecycleScheduler scheduler = new LifecycleScheduler(commandDispatchService, 25);

        when(commandDispatchService.dispatchReady(25)).thenReturn(2);
        when(commandDispatchService.dispatchRetriesDue(any(Instant.class), org.mockito.ArgumentMatchers.eq(25))).thenReturn(1);

        scheduler.runCycle();

        verify(commandDispatchService, times(1)).dispatchReady(25);
        verify(commandDispatchService, times(1)).dispatchRetriesDue(any(Instant.class), org.mockito.ArgumentMatchers.eq(25));
    }
}
