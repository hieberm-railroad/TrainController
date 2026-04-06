package com.traincontroller.interceptor;

import com.traincontroller.interceptor.config.InterceptorProperties;
import com.traincontroller.interceptor.metrics.InterceptorTelemetry;
import com.traincontroller.interceptor.model.AckStatus;
import com.traincontroller.interceptor.model.TurnoutIntent;
import com.traincontroller.interceptor.model.TurnoutState;
import com.traincontroller.interceptor.persistence.jdbc.JdbcCommandEventRepository;
import com.traincontroller.interceptor.persistence.jdbc.JdbcDeviceStateRepository;
import com.traincontroller.interceptor.persistence.jdbc.JdbcIntentRepository;
import com.traincontroller.interceptor.persistence.jdbc.JdbcTcCommandRepository;
import com.traincontroller.interceptor.service.AckIngestionService;
import com.traincontroller.interceptor.service.CommandDispatchService;
import com.traincontroller.interceptor.service.CommandTransportService;
import com.traincontroller.interceptor.service.CommandVerificationService;
import com.traincontroller.interceptor.service.IntentService;
import com.traincontroller.interceptor.transport.TransportSendResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntentLifecyclePersistenceTest {

    private JdbcTemplate jdbcTemplate;
    private IntentService intentService;
        private CommandDispatchService commandDispatchService;
        private AckIngestionService ackIngestionService;
        private CommandVerificationService commandVerificationService;
        private CommandTransportService commandTransportService;

        @AfterEach
        void cleanupState() {
                this.jdbcTemplate = null;
                this.intentService = null;
                this.commandDispatchService = null;
                this.ackIngestionService = null;
                this.commandVerificationService = null;
                this.commandTransportService = null;
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

        MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.4.0");
        mysqlContainer.withDatabaseName("train_controller");
        mysqlContainer.withUsername("train");
        mysqlContainer.withPassword("train");

        try (MySQLContainer<?> mysql = mysqlContainer) {
            mysql.start();

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(mysql.getJdbcUrl());
            ds.setUsername(mysql.getUsername());
            ds.setPassword(mysql.getPassword());

            this.jdbcTemplate = new JdbcTemplate(ds);

            NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(ds);
            this.intentService = new IntentService(
                    new JdbcIntentRepository(namedTemplate),
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    new JdbcDeviceStateRepository(namedTemplate),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
            );
            this.commandDispatchService = new CommandDispatchService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate)
            );
            this.ackIngestionService = new AckIngestionService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate)
            );
            this.commandVerificationService = new CommandVerificationService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcDeviceStateRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                    command -> java.util.Optional.empty(),
                    new InterceptorTelemetry(new SimpleMeterRegistry())
            );
            this.commandTransportService = new CommandTransportService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new com.traincontroller.interceptor.persistence.jdbc.JdbcCommandAttemptRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    this.ackIngestionService,
                    command -> TransportSendResult.noAck(),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                    new InterceptorTelemetry(new SimpleMeterRegistry())
            );

            applyMigrations(ds);
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

            Integer dispatched = tx.execute(statusTx -> commandDispatchService.dispatchReady(10));
            assertEquals(1, dispatched);

            String sentStatus = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals("SENT", sentStatus);

            Integer transportSent = tx.execute(statusTx -> commandTransportService.sendPending(10));
            assertEquals(1, transportSent);

            Integer attemptCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM command_attempt WHERE command_id = ?",
                    Integer.class,
                    "cmd-it-1"
            );
            assertEquals(1, attemptCount);

            List<String> sentLifecycleEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals(List.of("RECEIVED", "PERSISTED", "DISPATCH_READY", "SENT"), sentLifecycleEvents);

            Boolean ackProcessed = tx.execute(statusTx -> ackIngestionService.ingestAck("cmd-it-1", AckStatus.ACCEPTED));
            assertEquals(true, ackProcessed);

            String ackedStatus = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals("ACKED", ackedStatus);

            List<String> ackedLifecycleEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals(List.of("RECEIVED", "PERSISTED", "DISPATCH_READY", "SENT", "ACKED"), ackedLifecycleEvents);

            Boolean verified = tx.execute(statusTx -> commandVerificationService.reconcileAcked(
                    "cmd-it-1",
                    "OPEN",
                    Instant.parse("2026-04-06T13:00:02Z")
            ));
            assertEquals(true, verified);

            String verifiedStatus = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals("VERIFIED", verifiedStatus);

            List<String> verifiedLifecycleEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-1"
            );
            assertEquals(List.of("RECEIVED", "PERSISTED", "DISPATCH_READY", "SENT", "ACKED", "VERIFIED"), verifiedLifecycleEvents);
        }
    }

        @Test
        void handleDuplicateCommandIdIsIdempotentAcrossCoreTables() {
                Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                                "Docker is required for Testcontainers integration test");

                MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.4.0");
                mysqlContainer.withDatabaseName("train_controller");
                mysqlContainer.withUsername("train");
                mysqlContainer.withPassword("train");

                try (MySQLContainer<?> mysql = mysqlContainer) {
                        mysql.start();

                        DriverManagerDataSource ds = new DriverManagerDataSource();
                        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
                        ds.setUrl(mysql.getJdbcUrl());
                        ds.setUsername(mysql.getUsername());
                        ds.setPassword(mysql.getPassword());

                        this.jdbcTemplate = new JdbcTemplate(ds);

                        NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(ds);
                        this.intentService = new IntentService(
                                        new JdbcIntentRepository(namedTemplate),
                                        new JdbcTcCommandRepository(namedTemplate),
                                        new JdbcCommandEventRepository(namedTemplate),
                                        new JdbcDeviceStateRepository(namedTemplate),
                                        new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
                        );
                        this.commandDispatchService = new CommandDispatchService(
                                        new JdbcTcCommandRepository(namedTemplate),
                                        new JdbcCommandEventRepository(namedTemplate)
                        );
                        this.ackIngestionService = new AckIngestionService(
                                        new JdbcTcCommandRepository(namedTemplate),
                                        new JdbcCommandEventRepository(namedTemplate)
                        );

                        applyMigrations(ds);
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

    @Test
    void ackForNonSentCommandIsIgnoredInPersistenceFlow() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for Testcontainers integration test");

        MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.4.0");
        mysqlContainer.withDatabaseName("train_controller");
        mysqlContainer.withUsername("train");
        mysqlContainer.withPassword("train");

        try (MySQLContainer<?> mysql = mysqlContainer) {
            mysql.start();

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(mysql.getJdbcUrl());
            ds.setUsername(mysql.getUsername());
            ds.setPassword(mysql.getPassword());

            this.jdbcTemplate = new JdbcTemplate(ds);

            NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(ds);
            this.intentService = new IntentService(
                    new JdbcIntentRepository(namedTemplate),
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    new JdbcDeviceStateRepository(namedTemplate),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
            );
            this.ackIngestionService = new AckIngestionService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate)
            );

            applyMigrations(ds);
            resetTables();

            TurnoutIntent intent = new TurnoutIntent(
                    "cmd-it-nosent-1",
                    "corr-it-nosent-1",
                    "turnout-12",
                    TurnoutState.OPEN,
                    Instant.parse("2026-04-06T13:30:00Z")
            );

            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            tx.executeWithoutResult(status -> intentService.handle(intent));

            Boolean ackProcessed = tx.execute(status -> ackIngestionService.ingestAck("cmd-it-nosent-1", AckStatus.ACCEPTED));
            assertEquals(false, ackProcessed);

            String status = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-nosent-1"
            );
            assertEquals("DISPATCH_READY", status);

            List<String> lifecycleEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-nosent-1"
            );
            assertEquals(List.of("RECEIVED", "PERSISTED", "DISPATCH_READY"), lifecycleEvents);
        }
    }

    @Test
    void verifyMismatchSchedulesRetryInPersistenceFlow() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for Testcontainers integration test");

        MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.4.0");
        mysqlContainer.withDatabaseName("train_controller");
        mysqlContainer.withUsername("train");
        mysqlContainer.withPassword("train");

        try (MySQLContainer<?> mysql = mysqlContainer) {
            mysql.start();

            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(mysql.getJdbcUrl());
            ds.setUsername(mysql.getUsername());
            ds.setPassword(mysql.getPassword());

            this.jdbcTemplate = new JdbcTemplate(ds);

            NamedParameterJdbcTemplate namedTemplate = new NamedParameterJdbcTemplate(ds);
            this.intentService = new IntentService(
                    new JdbcIntentRepository(namedTemplate),
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    new JdbcDeviceStateRepository(namedTemplate),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200)
            );
            this.commandDispatchService = new CommandDispatchService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate)
            );
            this.ackIngestionService = new AckIngestionService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate)
            );
            this.commandVerificationService = new CommandVerificationService(
                    new JdbcTcCommandRepository(namedTemplate),
                    new JdbcDeviceStateRepository(namedTemplate),
                    new JdbcCommandEventRepository(namedTemplate),
                    new InterceptorProperties(750, 5, 500, "/dev/ttyUSB0", 19200),
                    command -> java.util.Optional.empty(),
                    new InterceptorTelemetry(new SimpleMeterRegistry())
            );

            applyMigrations(ds);
            resetTables();

            TurnoutIntent intent = new TurnoutIntent(
                    "cmd-it-retry-1",
                    "corr-it-retry-1",
                    "turnout-12",
                    TurnoutState.OPEN,
                    Instant.parse("2026-04-06T13:40:00Z")
            );

            TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
            tx.executeWithoutResult(status -> intentService.handle(intent));
            tx.execute(status -> commandDispatchService.dispatchReady(10));
            tx.execute(status -> ackIngestionService.ingestAck("cmd-it-retry-1", AckStatus.ACCEPTED));

            Boolean verified = tx.execute(status -> commandVerificationService.reconcileAcked(
                    "cmd-it-retry-1",
                    "CLOSED",
                    Instant.parse("2026-04-06T13:40:03Z")
            ));
            assertEquals(true, verified);

            String status = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-retry-1"
            );
            assertEquals("RETRY_SCHEDULED", status);

            Integer retryCount = jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM tc_command WHERE command_id = ?",
                    Integer.class,
                    "cmd-it-retry-1"
            );
            assertEquals(1, retryCount);

            List<String> lifecycleEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-retry-1"
            );
            assertEquals(
                    List.of("RECEIVED", "PERSISTED", "DISPATCH_READY", "SENT", "ACKED", "RETRY_SCHEDULED"),
                    lifecycleEvents
            );

            Integer dispatchedRetry = tx.execute(txStatus -> commandDispatchService.dispatchRetriesDue(
                    Instant.parse("2026-04-06T13:40:04Z"),
                    10
            ));
            assertEquals(1, dispatchedRetry);

            String resentStatus = jdbcTemplate.queryForObject(
                    "SELECT command_status FROM tc_command WHERE command_id = ?",
                    String.class,
                    "cmd-it-retry-1"
            );
            assertEquals("SENT", resentStatus);

            List<String> resentEvents = jdbcTemplate.queryForList(
                    "SELECT event_status FROM command_event WHERE command_id = ? ORDER BY id",
                    String.class,
                    "cmd-it-retry-1"
            );
            assertEquals(
                    List.of("RECEIVED", "PERSISTED", "DISPATCH_READY", "SENT", "ACKED", "RETRY_SCHEDULED", "SENT"),
                    resentEvents
            );
        }
    }

        private void applyMigrations(DataSource dataSource) {
        Path v1 = Path.of("..", "migrations", "V1__initial_schema.sql").toAbsolutePath().normalize();
        Path v2 = Path.of("..", "migrations", "V2__normalized_command_model.sql").toAbsolutePath().normalize();

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                                new FileSystemResource(Objects.requireNonNull(v1)),
                                new FileSystemResource(Objects.requireNonNull(v2))
        );
                DatabasePopulatorUtils.execute(populator, Objects.requireNonNull(dataSource));
    }
}
