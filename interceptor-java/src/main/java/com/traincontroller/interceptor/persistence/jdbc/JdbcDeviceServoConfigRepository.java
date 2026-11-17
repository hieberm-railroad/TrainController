package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.persistence.DeviceServoConfig;
import com.traincontroller.interceptor.persistence.DeviceServoConfigRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceServoConfigRepository implements DeviceServoConfigRepository {

    private static final String FIND_SQL = """
            SELECT device_id, open_angle, closed_angle
            FROM device_servo_config
            WHERE device_id IN (:deviceIds)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDeviceServoConfigRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DeviceServoConfig> findByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }

        List<String> candidateIds = candidateDeviceIds(deviceId);
        if (candidateIds.isEmpty()) {
            return Optional.empty();
        }

        return jdbcTemplate.query(
                FIND_SQL,
                new MapSqlParameterSource("deviceIds", candidateIds),
                rs -> rs.next()
                        ? Optional.of(new DeviceServoConfig(
                                rs.getString("device_id"),
                                rs.getInt("open_angle"),
                                rs.getInt("closed_angle")))
                        : Optional.empty()
        );
    }

    private static List<String> candidateDeviceIds(String deviceId) {
        Set<String> candidateIds = new LinkedHashSet<>();
        String trimmed = deviceId.trim();
        candidateIds.add(trimmed);

        String digitsOnly = trimmed.replaceAll("\\D+", "");
        if (!digitsOnly.isEmpty()) {
            int numericValue = Integer.parseInt(digitsOnly);
            candidateIds.add(String.valueOf(numericValue));
            candidateIds.add(String.format(Locale.ROOT, "%03d", numericValue));
            candidateIds.add(String.format(Locale.ROOT, "%d", numericValue));
            if (numericValue > 0) {
                candidateIds.add(String.valueOf(numericValue - 1));
            }
        }

        String normalizedTurnout = trimmed.replaceFirst("(?i)^turnout", "");
        if (!normalizedTurnout.equals(trimmed)) {
            candidateIds.add(normalizedTurnout);
            if (!normalizedTurnout.isBlank()) {
                String turnoutDigits = normalizedTurnout.replaceAll("\\D+", "");
                if (!turnoutDigits.isEmpty()) {
                    int turnoutValue = Integer.parseInt(turnoutDigits);
                    candidateIds.add(String.valueOf(turnoutValue));
                    candidateIds.add(String.format(Locale.ROOT, "%03d", turnoutValue));
                }
            }
        }

        return new ArrayList<>(candidateIds);
    }
}
