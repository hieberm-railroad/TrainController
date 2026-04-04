package com.traincontroller.interceptor.service;

import com.traincontroller.interceptor.model.TurnoutIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);

    public void handle(TurnoutIntent intent) {
        // Placeholder pipeline hook: persist desired state, enqueue dispatch, and trigger reconciliation.
        log.info("Received intent commandId={} turnoutId={} desiredState={}",
                intent.commandId(), intent.turnoutId(), intent.desiredState());
    }
}
