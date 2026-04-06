CREATE TABLE IF NOT EXISTS device (
    id VARCHAR(64) PRIMARY KEY,
    device_type VARCHAR(16) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    external_ref VARCHAR(128) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_device_type
        CHECK (device_type IN ('TURNOUT', 'SIGNAL')),
    INDEX idx_device_type_id (device_type, id),
    INDEX idx_device_node_id (node_id)
);

CREATE TABLE IF NOT EXISTS intent (
    intent_id VARCHAR(64) PRIMARY KEY,
    correlation_id VARCHAR(64) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    desired_state VARCHAR(32) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(16) NOT NULL,
    reject_reason VARCHAR(255) NULL,
    CONSTRAINT chk_intent_status
        CHECK (status IN ('RECEIVED', 'REJECTED', 'PROCESSED', 'CANCELLED')),
    CONSTRAINT fk_intent_device
        FOREIGN KEY (device_id)
        REFERENCES device (id),
    CONSTRAINT uq_intent_correlation_device UNIQUE (correlation_id, device_id),
    INDEX idx_intent_status_requested_at (status, requested_at)
);

CREATE TABLE IF NOT EXISTS tc_command (
    command_id VARCHAR(64) PRIMARY KEY,
    intent_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    desired_state VARCHAR(32) NOT NULL,
    command_status VARCHAR(24) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 0,
    settle_delay_ms INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NULL,
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_command_status
        CHECK (command_status IN (
            'RECEIVED', 'PERSISTED', 'DISPATCH_READY', 'SENT', 'ACKED',
            'VERIFY_PENDING', 'VERIFIED', 'RETRY_SCHEDULED', 'FAILED', 'CANCELLED'
        )),
    CONSTRAINT chk_command_retry_count_nonnegative
        CHECK (retry_count >= 0),
    CONSTRAINT chk_command_max_retries_nonnegative
        CHECK (max_retries >= 0),
    CONSTRAINT chk_command_settle_delay_nonnegative
        CHECK (settle_delay_ms >= 0),
    CONSTRAINT fk_command_intent
        FOREIGN KEY (intent_id)
        REFERENCES intent (intent_id),
    CONSTRAINT fk_command_device
        FOREIGN KEY (device_id)
        REFERENCES device (id),
    INDEX idx_command_status_next_attempt (command_status, next_attempt_at),
    INDEX idx_command_device_created (device_id, created_at),
    INDEX idx_command_correlation (correlation_id)
);

CREATE TABLE IF NOT EXISTS device_state (
    device_id VARCHAR(64) PRIMARY KEY,
    desired_state VARCHAR(32) NOT NULL,
    actual_state VARCHAR(32) NULL,
    last_command_id VARCHAR(64) NULL,
    state_quality VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    last_verified_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_device_state_quality
        CHECK (state_quality IN ('GOOD', 'DEGRADED', 'UNKNOWN')),
    CONSTRAINT fk_device_state_device
        FOREIGN KEY (device_id)
        REFERENCES device (id),
    CONSTRAINT fk_device_state_last_command
        FOREIGN KEY (last_command_id)
        REFERENCES tc_command (command_id),
    INDEX idx_device_state_updated_at (updated_at)
);

CREATE TABLE IF NOT EXISTS command_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id VARCHAR(64) NOT NULL,
    attempt_no INT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acked_at TIMESTAMP NULL,
    ack_status VARCHAR(24) NULL,
    transport_error VARCHAR(255) NULL,
    verify_started_at TIMESTAMP NULL,
    verify_completed_at TIMESTAMP NULL,
    verified BOOLEAN NULL,
    CONSTRAINT chk_command_attempt_ack_status
        CHECK (ack_status IN ('ACCEPTED', 'REJECTED', 'DUPLICATE', 'STALE') OR ack_status IS NULL),
    CONSTRAINT chk_command_attempt_no_positive
        CHECK (attempt_no > 0),
    CONSTRAINT fk_attempt_command
        FOREIGN KEY (command_id)
        REFERENCES tc_command (command_id),
    CONSTRAINT uq_attempt_command_no UNIQUE (command_id, attempt_no),
    INDEX idx_attempt_command_sent (command_id, sent_at)
);

CREATE TABLE IF NOT EXISTS command_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id VARCHAR(64) NOT NULL,
    intent_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_status VARCHAR(24) NOT NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_command
        FOREIGN KEY (command_id)
        REFERENCES tc_command (command_id),
    CONSTRAINT fk_event_intent
        FOREIGN KEY (intent_id)
        REFERENCES intent (intent_id),
    INDEX idx_event_command_created (command_id, created_at),
    INDEX idx_event_intent_created (intent_id, created_at)
);
