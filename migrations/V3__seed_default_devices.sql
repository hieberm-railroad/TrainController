INSERT INTO device (id, device_type, node_id, external_ref, enabled)
VALUES
    ('turnout1', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('signal1', 'SIGNAL', 'signal1', NULL, TRUE)
ON DUPLICATE KEY UPDATE
    device_type = VALUES(device_type),
    node_id = VALUES(node_id),
    enabled = VALUES(enabled);
