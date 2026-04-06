package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.persistence.CommandEventEntity;
import com.traincontroller.interceptor.persistence.CommandEventRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCommandEventRepository implements CommandEventRepository {

    private static final String INSERT_SQL = """
            INSERT INTO command_event (
                command_id, intent_id, event_type, event_status, detail_json
            ) VALUES (
                :commandId, :intentId, :eventType, :eventStatus, CAST(:detailJson AS JSON)
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCommandEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(CommandEventEntity event) {
        jdbcTemplate.update(
                INSERT_SQL,
            new MapSqlParameterSource()
                .addValue("commandId", event.commandId())
                .addValue("intentId", event.intentId())
                .addValue("eventType", event.eventType())
                .addValue("eventStatus", event.eventStatus())
                .addValue("detailJson", event.detailJson() == null ? "null" : event.detailJson())
        );
    }
}
