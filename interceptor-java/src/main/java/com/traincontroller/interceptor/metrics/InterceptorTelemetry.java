package com.traincontroller.interceptor.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class InterceptorTelemetry {

    private final MeterRegistry meterRegistry;

    public InterceptorTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTransportResult(String outcome, Duration duration) {
        meterRegistry.counter("interceptor.transport.send.total", "outcome", outcome).increment();
        Timer.builder("interceptor.transport.send.duration")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordTransportRetryScheduled() {
        meterRegistry.counter("interceptor.transport.retry_scheduled.total").increment();
    }

    public void recordTransportFailed() {
        meterRegistry.counter("interceptor.transport.failed.total").increment();
    }

    public void recordReadbackResult(String outcome, Duration duration) {
        meterRegistry.counter("interceptor.readback.total", "outcome", outcome).increment();
        Timer.builder("interceptor.readback.duration")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordVerificationOutcome(String outcome) {
        meterRegistry.counter("interceptor.verification.total", "outcome", outcome).increment();
    }

    public void recordCommandLifecycleLatency(String outcome, Duration duration) {
        Timer.builder("interceptor.command.lifecycle.duration")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }
}