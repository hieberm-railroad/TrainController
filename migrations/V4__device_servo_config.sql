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
    ('1', 428, 187),
    ('2', 279, 411),
    ('3', 240, 356),
    ('4', 211, 345),
    ('5', 222, 354),
    ('6', 319, 211),
    ('7', 253, 437),
    ('8', 174, 376),
    ('9', 472, 248),
    ('10', 163, 279),
    ('11', 444, 338);
