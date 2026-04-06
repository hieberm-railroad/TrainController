package com.traincontroller.interceptor;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.persistence.jdbc.JdbcCommandEventRepository;
import com.traincontroller.interceptor.persistence.jdbc.JdbcDeviceStateRepository;
import com.traincontroller.interceptor.persistence.jdbc.JdbcIntentRepository;
import com.traincontroller.interceptor.persistence.jdbc.JdbcTcCommandRepository;
import com.traincontroller.interceptor.service.IntentService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntentLifecyclePersistenceTest {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private IntentService intentService;

        @AfterEach
        void cleanupState() {
                this.dataSource = null;
                this.jdbcTemplate = null;
                this.intentService = null;
        }

    @BeforeEach
    void resetTables() {
                if (jdbcTemplate == null) {
                        return;
                }

        jdbcTemplate.execute("DELETE FROM command_event");
        jdbcTemplate.execute("DELETE FROM command_attempt");
        jdbcTemplate.execute("DELETE FROM device_state");
        jdbcTemplate.execute("DELETE FROM tc_command");
        jdbcTemplate.execute("DELETE FROM intent");
        jdbcTemplate.execute("DELETE FROM device");

        jdbcTemplate.update(
                "INSERT INTO device (id, device_type, node_id, enabled) VALUES (?, ?, ?, ?)",
                "turnout-12", "TURNOUT", "turnout-12", true
        );
    }

    @Test
    void handlePersistsAtomicLifecycleAcrossCoreTables() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for Testcontainers integration test");

        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
                .withDatabaseName("train_controller")
                .withUsername("train")
                .withPassword("train")) {
            mysql.start();

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(mysql.getJdbcUrl());
            ds.setUsername(mysql.getUsername());
            ds.setPassword(mysql.getPassword());

            this.dataSource = ds;
            this.jdbcTemplate = new JdbcTemplate(ds);

            NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(ds);
            this.intentService = new IntentService(
                    new JdbcIntentRepository(namedTemplate),
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    new JdbcDeviceStateRepository(namedTemplate),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
            );

            applyMigrations();
            resetTables();

            TurnoutIntent intent = new TurnoutIntent(
                    "cmd-it-1",
                    "corr-it-1",
                    "turnout-12",
                    TurnoutState.OPEN,
                    Instant.parse("2026-04-06T13:00:00Z")
            );

            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            tx.executeWithoutResult(status -> intentService.handle(intent));

            Integer intentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intent", Integer.class);
            Integer commandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tc_command", Integer.class);
            Integer eventCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM command_event", Integer.class);
            Integer deviceStateCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_state", Integer.class);

            assertEquals(1, intentCount);
            assertEquals(1, commandCount);
            assertEquals(3, eventCount);
            assertEquals(1, deviceStateCount);

            String status = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals("DISPATCH_READY", status);

            List<String> lifecycleEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals(List.of("RECEIVED", "PERSISTED", "DISPATCH_READY"), lifecycleEvents);

            String desiredState = jdbcTemplate.queryForObject(
                    "SELECT desired_state FROM device_state WHERE device_id = ?",
                    String.class,
                    "turnout-12"
            );
            assertEquals("OPEN", desiredState);

            String persistedIntentId = jdbcTemplate.queryForObject(
                    "SELECT intent_id FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-1"
            );
            assertNotNull(persistedIntentId);
        }
    }

        @Test
        void handleDuplicateCommandIdIsIdempotentAcrossCoreTables() {
                Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                                "Docker is required for Testcontainers integration test");

                try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
                                .withDatabaseName("train_controller")
                                .withUsername("train")
                                .withPassword("train")) {
                        mysql.start();

                        DriverManagerDataSource ds = new DriverManagerDataSource();
                        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
                        ds.setUrl(mysql.getJdbcUrl());
                        ds.setUsername(mysql.getUsername());
                        ds.setPassword(mysql.getPassword());

                        this.dataSource = ds;
                        this.jdbcTemplate = new JdbcTemplate(ds);

                        NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(ds);
                        this.intentService = new IntentService(
                                        new JdbcIntentRepository(namedTemplate),
                                        new JdbcTcCommandRepository(namedTemplate),
                                        new JdbcCommandEventRepository(namedTemplate),
                                        new JdbcDeviceStateRepository(namedTemplate),
                                        new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
                        );

                        applyMigrations();
                        resetTables();

                        TurnoutIntent intent = new TurnoutIntent(
                                        "cmd-it-dup-1",
                                        "corr-it-dup-1",
                                        "turnout-12",
                                        TurnoutState.OPEN,
                                        Instant.parse("2026-04-06T13:15:00Z")
                        );

                        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
                        tx.executeWithoutResult(status -> intentService.handle(intent));
                        assertDoesNotThrow(() -> tx.executeWithoutResult(status -> intentService.handle(intent)));

                        Integer intentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intent", Integer.class);
                        Integer commandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tc_command", Integer.class);
                        Integer eventCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM command_event", Integer.class);
                        Integer deviceStateCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_state", Integer.class);

                        assertEquals(1, intentCount);
                        assertEquals(1, commandCount);
                        assertEquals(3, eventCount);
                        assertEquals(1, deviceStateCount);
                }
        }

    private void applyMigrations() {
        Path v1 = Path.of("..", "migrations", "V1__initial_schema.sql").toAbsolutePath().normalize();
        Path v2 = Path.of("..", "migrations", "V2__normalized_command_model.sql").toAbsolutePath().normalize();

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource(v1),
                new FileSystemResource(v2)
        );
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
