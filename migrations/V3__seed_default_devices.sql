INSERT INTO device (id, device_type, node_id, external_ref, enabled)
VALUES
    ('001', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('002', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('003', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('004', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('005', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('006', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('007', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('008', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('009', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('010', 'TURNOUT', 'turnout-node', NULL, TRUE),
    ('signal1', 'SIGNAL', 'signal1', NULL, TRUE)
ON DUPLICATE KEY UPDATE
    device_type = VALUES(device_type),
    node_id = VALUES(node_id),
    enabled = VALUES(enabled);
