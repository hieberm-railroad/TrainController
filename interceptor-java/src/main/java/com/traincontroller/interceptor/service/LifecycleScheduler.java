package com.traincontroller.interceptor.service;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleScheduler.class);

    private final CommandDispatchService commandDispatchService;
    private final CommandTransportService commandTransportService;
    private final int batchSize;

    public LifecycleScheduler(
            CommandDispatchService commandDispatchService,
            CommandTransportService commandTransportService,
            @Value("${interceptor.scheduler.batch-size:50}") int batchSize
    ) {
        this.commandDispatchService = commandDispatchService;
        this.commandTransportService = commandTransportService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${interceptor.scheduler.delay-ms:500}")
    public void runCycle() {
        int dispatchedReady = commandDispatchService.dispatchReady(batchSize);
        int dispatchedRetries = commandDispatchService.dispatchRetriesDue(Instant.now(), batchSize);
        int sent = commandTransportService.sendPending(batchSize);

        if (dispatchedReady > 0 || dispatchedRetries > 0 || sent > 0) {
            log.info("Lifecycle scheduler cycle dispatchedReady={} dispatchedRetries={} sent={}",
                    dispatchedReady, dispatchedRetries, sent);
        }
    }
}
