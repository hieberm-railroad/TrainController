CREATE TABLE IF NOT EXISTS turnout_state (
    turnout_id VARCHAR(64) PRIMARY KEY,
    desired_state VARCHAR(16) NOT NULL,
    actual_state VARCHAR(16) NULL,
    command_status VARCHAR(16) NOT NULL,
    last_command_id VARCHAR(64) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_verified_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS command_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    turnout_id VARCHAR(64) NOT NULL,
    desired_state VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_command_history_command_id (command_id),
    INDEX idx_command_history_turnout_id (turnout_id)
);
