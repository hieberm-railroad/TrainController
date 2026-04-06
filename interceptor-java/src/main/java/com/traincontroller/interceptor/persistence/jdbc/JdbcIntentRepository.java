package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.persistence.IntentEntity;
import com.traincontroller.interceptor.persistence.IntentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
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

        private static final String FIND_INTENT_ID_SQL = """
                        SELECT intent_id
                        FROM intent
                        WHERE correlation_id = :correlationId
                            AND device_id = :deviceId
                        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcIntentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String upsert(IntentEntity intent) {
        MapSqlParameterSource keyParams = new MapSqlParameterSource()
            .addValue("correlationId", intent.correlationId())
            .addValue("deviceId", intent.deviceId());

        String existingIntentId = jdbcTemplate.query(
                FIND_INTENT_ID_SQL,
                keyParams,
                rs -> rs.next() ? rs.getString("intent_id") : null
        );
        if (existingIntentId != null) {
            return existingIntentId;
        }

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

        try {
            jdbcTemplate.update(INSERT_SQL, params);
            return intent.intentId();
        } catch (DuplicateKeyException ignored) {
            String persistedIntentId = jdbcTemplate.query(
                    FIND_INTENT_ID_SQL,
                    keyParams,
                    rs -> rs.next() ? rs.getString("intent_id") : null
            );
            if (persistedIntentId != null) {
                return persistedIntentId;
            }
            throw ignored;
        }
    }

    private static Timestamp asTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
