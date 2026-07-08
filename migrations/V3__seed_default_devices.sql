-- Numeric device IDs (internal reference)
INSERT INTO device (id, device_type, node_id, external_ref, enabled)
VALUES
    ('001', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('002', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('003', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('004', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('005', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('006', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('007', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('008', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('009', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('010', 'TURNOUT', 'turnout1', NULL, TRUE),
    ('signal1', 'SIGNAL', 'signal1', NULL, TRUE)
ON DUPLICATE KEY UPDATE
    device_type = VALUES(device_type),
    node_id = VALUES(node_id),
    enabled = VALUES(enabled);

-- JMRI-style logical device IDs (maps to MQTT topics from JMRI)
INSERT INTO device (id, device_type, node_id, external_ref, enabled)
VALUES
    ('turnout1', 'TURNOUT', 'turnout1', '001', TRUE),
    ('turnout2', 'TURNOUT', 'turnout1', '002', TRUE),
    ('turnout3', 'TURNOUT', 'turnout1', '003', TRUE),
    ('turnout4', 'TURNOUT', 'turnout1', '004', TRUE),
    ('turnout5', 'TURNOUT', 'turnout1', '005', TRUE),
    ('turnout6', 'TURNOUT', 'turnout1', '006', TRUE),
    ('turnout7', 'TURNOUT', 'turnout1', '007', TRUE),
    ('turnout8', 'TURNOUT', 'turnout1', '008', TRUE),
    ('turnout9', 'TURNOUT', 'turnout1', '009', TRUE),
    ('turnout10', 'TURNOUT', 'turnout1', '010', TRUE)
ON DUPLICATE KEY UPDATE
    device_type = VALUES(device_type),
    node_id = VALUES(node_id),
    enabled = VALUES(enabled);
