CREATE TABLE IF NOT EXISTS device_servo_config (
    device_id    VARCHAR(64)       NOT NULL,
    open_angle   SMALLINT UNSIGNED NOT NULL DEFAULT 238,
    closed_angle SMALLINT UNSIGNED NOT NULL DEFAULT 317,
    updated_at   TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_device_servo_config PRIMARY KEY (device_id),
    CONSTRAINT fk_servo_config_device FOREIGN KEY (device_id) REFERENCES device (id)
);

-- Default angles are raw PCA9685 pulse counts (0-4095). Keep an existing calibration if it has already been saved,
-- and only seed the defaults when the table is still empty.
INSERT IGNORE INTO device_servo_config (device_id, open_angle, closed_angle) VALUES
    ('001', 317, 238),
    ('002', 354, 233),
    ('003', 424, 233),
    ('004', 185, 343),
    ('005', 178, 376),
    ('006',  82, 464),
    ('007', 150, 343),
    ('008', 207, 389),
    ('009', 152, 292),
    ('010', 378, 209);
