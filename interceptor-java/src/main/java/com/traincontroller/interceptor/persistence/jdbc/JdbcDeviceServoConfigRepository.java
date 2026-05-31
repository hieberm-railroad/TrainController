package com.traincontroller.interceptor.persistence.jdbc;

import com.traincontroller.interceptor.persistence.DeviceServoConfig;
import com.traincontroller.interceptor.persistence.DeviceServoConfigRepository;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceServoConfigRepository implements DeviceServoConfigRepository {

    private static final String FIND_SQL = """
            SELECT device_id, open_angle, closed_angle
            FROM device_servo_config
            WHERE device_id = :deviceId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDeviceServoConfigRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DeviceServoConfig> findByDeviceId(String deviceId) {
        return jdbcTemplate.query(
                FIND_SQL,
                new MapSqlParameterSource("deviceId", deviceId),
                rs -> rs.next()
                        ? Optional.of(new DeviceServoConfig(
                                rs.getString("device_id"),
                                rs.getInt("open_angle"),
                                rs.getInt("closed_angle")))
                        : Optional.empty()
        );
    }
}
