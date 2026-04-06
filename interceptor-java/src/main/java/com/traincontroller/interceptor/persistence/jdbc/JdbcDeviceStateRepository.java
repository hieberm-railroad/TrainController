package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.persistence.DeviceStateEntity;
import com.traincontroller.interceptor.persistence.DeviceStateRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceStateRepository implements DeviceStateRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO device_state (
                device_id, desired_state, actual_state, last_command_id, state_quality, last_verified_at
            ) VALUES (
                :deviceId, :desiredState, :actualState, :lastCommandId, :stateQuality, :lastVerifiedAt
            )
            ON DUPLICATE KEY UPDATE
                desired_state = VALUES(desired_state),
                actual_state = VALUES(actual_state),
                last_command_id = VALUES(last_command_id),
                state_quality = VALUES(state_quality),
                last_verified_at = VALUES(last_verified_at),
                updated_at = CURRENT_TIMESTAMP
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDeviceStateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(DeviceStateEntity state) {
        jdbcTemplate.update(
                UPSERT_SQL,
            new MapSqlParameterSource()
                .addValue("deviceId", state.deviceId())
                .addValue("desiredState", state.desiredState())
                .addValue("actualState", state.actualState())
                .addValue("lastCommandId", state.lastCommandId())
                .addValue("stateQuality", state.stateQuality().name())
                .addValue("lastVerifiedAt", asTimestamp(state.lastVerifiedAt()))
        );
    }

    private static Timestamp asTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
