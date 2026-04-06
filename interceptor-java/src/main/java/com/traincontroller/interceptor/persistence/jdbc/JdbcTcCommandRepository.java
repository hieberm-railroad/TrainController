package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.model.CommandStatus;
import com.traincontroller.interceptor.persistence.TcCommandEntity;
import com.traincontroller.interceptor.persistence.TcCommandRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTcCommandRepository implements TcCommandRepository {

    private static final String INSERT_SQL = """
            INSERT INTO tc_command (
                command_id, intent_id, correlation_id, device_id, node_id, operation_type,
                desired_state, command_status, retry_count, max_retries, settle_delay_ms,
                next_attempt_at, failure_reason
            ) VALUES (
                :commandId, :intentId, :correlationId, :deviceId, :nodeId, :operationType,
                :desiredState, :commandStatus, :retryCount, :maxRetries, :settleDelayMs,
                :nextAttemptAt, :failureReason
            )
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE tc_command
            SET command_status = :commandStatus,
                failure_reason = :failureReason,
                updated_at = CURRENT_TIMESTAMP
            WHERE command_id = :commandId
            """;

    private static final String SELECT_BY_STATUS_SQL = """
            SELECT
                command_id,
                intent_id,
                correlation_id,
                device_id,
                node_id,
                operation_type,
                desired_state,
                command_status,
                retry_count,
                max_retries,
                settle_delay_ms,
                next_attempt_at,
                failure_reason
            FROM tc_command
            WHERE command_status = :commandStatus
            ORDER BY created_at
            LIMIT :limitRows
            """;

    private static final String UPDATE_STATUS_IF_CURRENT_SQL = """
            UPDATE tc_command
            SET command_status = :newStatus,
                failure_reason = :failureReason,
                updated_at = CURRENT_TIMESTAMP
            WHERE command_id = :commandId
              AND command_status = :expectedStatus
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTcCommandRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(TcCommandEntity command) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("commandId", command.commandId())
            .addValue("intentId", command.intentId())
            .addValue("correlationId", command.correlationId())
            .addValue("deviceId", command.deviceId())
            .addValue("nodeId", command.nodeId())
            .addValue("operationType", command.operationType().name())
            .addValue("desiredState", command.desiredState())
            .addValue("commandStatus", command.commandStatus().name())
            .addValue("retryCount", command.retryCount())
            .addValue("maxRetries", command.maxRetries())
            .addValue("settleDelayMs", command.settleDelayMs())
            .addValue("nextAttemptAt", asTimestamp(command.nextAttemptAt()))
            .addValue("failureReason", command.failureReason());
        jdbcTemplate.update(INSERT_SQL, params);
    }

    @Override
    public int updateStatus(String commandId, CommandStatus status, String failureReason) {
        return jdbcTemplate.update(
                UPDATE_STATUS_SQL,
            new MapSqlParameterSource()
                .addValue("commandId", commandId)
                .addValue("commandStatus", status.name())
                .addValue("failureReason", failureReason)
        );
    }

    @Override
    public List<TcCommandEntity> findByStatus(CommandStatus status, int limit) {
        return jdbcTemplate.query(
                SELECT_BY_STATUS_SQL,
                new MapSqlParameterSource()
                        .addValue("commandStatus", status.name())
                        .addValue("limitRows", limit),
                (rs, rowNum) -> mapRow(rs)
        );
    }

    @Override
    public int updateStatusIfCurrent(
            String commandId,
            CommandStatus expectedStatus,
            CommandStatus newStatus,
            String failureReason
    ) {
        return jdbcTemplate.update(
                UPDATE_STATUS_IF_CURRENT_SQL,
                new MapSqlParameterSource()
                        .addValue("commandId", commandId)
                        .addValue("expectedStatus", expectedStatus.name())
                        .addValue("newStatus", newStatus.name())
                        .addValue("failureReason", failureReason)
        );
    }

    private static TcCommandEntity mapRow(ResultSet rs) throws SQLException {
        return new TcCommandEntity(
                rs.getString("command_id"),
                rs.getString("intent_id"),
                rs.getString("correlation_id"),
                rs.getString("device_id"),
                rs.getString("node_id"),
                com.traincontroller.interceptor.model.OperationType.valueOf(rs.getString("operation_type")),
                rs.getString("desired_state"),
                CommandStatus.valueOf(rs.getString("command_status")),
                rs.getInt("retry_count"),
                rs.getInt("max_retries"),
                rs.getInt("settle_delay_ms"),
                toInstant(rs.getTimestamp("next_attempt_at")),
                rs.getString("failure_reason")
        );
    }

    private static Timestamp asTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
