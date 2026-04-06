package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.persistence.CommandAttemptRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCommandAttemptRepository implements CommandAttemptRepository {

    private static final String INSERT_SENT_SQL = """
            INSERT INTO command_attempt (
                command_id, attempt_no, sent_at
            ) VALUES (
                :commandId, :attemptNo, :sentAt
            )
            """;

    private static final String UPDATE_ACK_SQL = """
            UPDATE command_attempt
            SET acked_at = :ackedAt,
                ack_status = :ackStatus
            WHERE command_id = :commandId
              AND attempt_no = :attemptNo
            """;

    private static final String UPDATE_TRANSPORT_ERROR_SQL = """
            UPDATE command_attempt
            SET transport_error = :transportError
            WHERE command_id = :commandId
              AND attempt_no = :attemptNo
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCommandAttemptRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertSentAttempt(String commandId, int attemptNo, Instant sentAt) {
        jdbcTemplate.update(
                INSERT_SENT_SQL,
                new MapSqlParameterSource()
                        .addValue("commandId", commandId)
                        .addValue("attemptNo", attemptNo)
                        .addValue("sentAt", asTimestamp(sentAt))
        );
    }

    @Override
    public int markAck(String commandId, int attemptNo, AckStatus ackStatus, Instant ackedAt) {
        return jdbcTemplate.update(
                UPDATE_ACK_SQL,
                new MapSqlParameterSource()
                        .addValue("commandId", commandId)
                        .addValue("attemptNo", attemptNo)
                        .addValue("ackedAt", asTimestamp(ackedAt))
                        .addValue("ackStatus", ackStatus.name())
        );
    }

    @Override
    public int markTransportError(String commandId, int attemptNo, String transportError) {
        return jdbcTemplate.update(
                UPDATE_TRANSPORT_ERROR_SQL,
                new MapSqlParameterSource()
                        .addValue("commandId", commandId)
                        .addValue("attemptNo", attemptNo)
                        .addValue("transportError", transportError)
        );
    }

    private static Timestamp asTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
