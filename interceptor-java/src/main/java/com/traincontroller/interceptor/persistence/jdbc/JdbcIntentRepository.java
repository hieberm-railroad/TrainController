package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.persistence.IntentEntity;
import com.traincontroller.interceptor.persistence.IntentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIntentRepository implements IntentRepository {

    private static final String INSERT_SQL = """
            INSERT INTO intent (
                intent_id, correlation_id, source_system, device_id, operation_type,
                desired_state, requested_at, accepted_at, status, reject_reason
            ) VALUES (
                :intentId, :correlationId, :sourceSystem, :deviceId, :operationType,
                :desiredState, :requestedAt, :acceptedAt, :status, :rejectReason
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcIntentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(IntentEntity intent) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("intentId", intent.intentId())
            .addValue("correlationId", intent.correlationId())
            .addValue("sourceSystem", intent.sourceSystem())
            .addValue("deviceId", intent.deviceId())
            .addValue("operationType", intent.operationType().name())
            .addValue("desiredState", intent.desiredState())
            .addValue("requestedAt", asTimestamp(intent.requestedAt()))
            .addValue("acceptedAt", asTimestamp(intent.acceptedAt()))
            .addValue("status", intent.status().name())
            .addValue("rejectReason", intent.rejectReason());
        jdbcTemplate.update(INSERT_SQL, params);
    }

    private static Timestamp asTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
