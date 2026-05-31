CREATE TABLE IF NOT EXISTS device_servo_config (
    device_id    VARCHAR(64)       NOT NULL,
    open_angle   SMALLINT UNSIGNED NOT NULL DEFAULT 238,
    closed_angle SMALLINT UNSIGNED NOT NULL DEFAULT 317,
    updated_at   TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_device_servo_config PRIMARY KEY (device_id),
    CONSTRAINT fk_servo_config_device FOREIGN KEY (device_id) REFERENCES device (id)
);

-- Default angles are raw PCA9685 pulse counts (0-4095). Update per-servo after physical calibration.
INSERT INTO device_servo_config (device_id, open_angle, closed_angle) VALUES
    ('001', 238, 317),
    ('002', 233, 354),
    ('003', 233, 424),
    ('004', 343, 185),
    ('005', 376, 178),
    ('006', 464,  82),
    ('007', 343, 150),
    ('008', 389, 207),
    ('009', 292, 152),
    ('010', 209, 378)
ON DUPLICATE KEY UPDATE
    open_angle   = VALUES(open_angle),
    closed_angle = VALUES(closed_angle);
