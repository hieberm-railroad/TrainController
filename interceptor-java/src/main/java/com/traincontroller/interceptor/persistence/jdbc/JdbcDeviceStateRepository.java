package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.model.StateQuality;
import com.traincontroller.interceptor.persistence.DeviceStateEntity;
import com.traincontroller.interceptor.persistence.DeviceStateRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
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

    private static final String FIND_LATEST_BY_DEVICE_ID_SQL = """
            SELECT device_id, desired_state, actual_state, last_command_id, state_quality, last_verified_at
            FROM device_state
            WHERE device_id = :deviceId
            ORDER BY updated_at DESC
            LIMIT 1
            """;

    private static final RowMapper<DeviceStateEntity> ROW_MAPPER = (rs, rowNum) -> toEntity(rs);

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

    @Override
    public Optional<DeviceStateEntity> findLatestByDeviceId(String deviceId) {
        return jdbcTemplate.query(
                FIND_LATEST_BY_DEVICE_ID_SQL,
                new MapSqlParameterSource("deviceId", deviceId),
                rs -> rs.next() ? Optional.of(toEntity(rs)) : Optional.empty()
        );
    }

    private static DeviceStateEntity toEntity(ResultSet rs) throws SQLException {
        return new DeviceStateEntity(
                rs.getString("device_id"),
                rs.getString("desired_state"),
                rs.getString("actual_state"),
                rs.getString("last_command_id"),
                StateQuality.valueOf(rs.getString("state_quality")),
                asInstant(rs.getTimestamp("last_verified_at"))
        );
    }

    private static Timestamp asTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant asInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
