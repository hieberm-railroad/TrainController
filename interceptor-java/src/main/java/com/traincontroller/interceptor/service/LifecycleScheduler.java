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
    private final CommandVerificationService commandVerificationService;
    private final int batchSize;

    public LifecycleScheduler(
            CommandDispatchService commandDispatchService,
            CommandTransportService commandTransportService,
            CommandVerificationService commandVerificationService,
            @Value("${interceptor.scheduler.batch-size:50}") int batchSize
    ) {
        this.commandDispatchService = commandDispatchService;
        this.commandTransportService = commandTransportService;
        this.commandVerificationService = commandVerificationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${interceptor.scheduler.delay-ms:500}")
    public void runCycle() {
        int dispatchedReady = commandDispatchService.dispatchReady(batchSize);
        int dispatchedRetries = commandDispatchService.dispatchRetriesDue(Instant.now(), batchSize);
        int sent = commandTransportService.sendPending(batchSize);
        int reconciled = commandVerificationService.pollAndReconcileAcked(batchSize);

        if (dispatchedReady > 0 || dispatchedRetries > 0 || sent > 0 || reconciled > 0) {
            log.info("Lifecycle scheduler cycle dispatchedReady={} dispatchedRetries={} sent={} reconciled={}",
                    dispatchedReady, dispatchedRetries, sent, reconciled);
        }
    }
}
