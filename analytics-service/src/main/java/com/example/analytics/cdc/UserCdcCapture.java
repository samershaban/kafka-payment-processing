package com.example.analytics.cdc;

import com.example.analytics.kafka.AnalyticsPublisher;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams every change to the users table (Postgres) to analytics.users.raw in real time, using an embedded
 * Debezium engine that reads Postgres's write-ahead log through logical replication (pgoutput plugin).
 *
 * On the very first start it publishes every existing user (snapshot, op = r), then each insert/update/delete as
 * it happens. Its position is saved in the offset file, so a restart continues where it stopped. A record is only
 * marked as processed after Kafka has acknowledged it (at-least-once; eventIds let Flink drop repeats).
 *
 * If Postgres isn't prepared (scripts/setup-postgres-cdc.sql), the engine stops with an error that is logged and
 * shown by status(); the rest of the service keeps running.
 */
@Component
public class UserCdcCapture implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UserCdcCapture.class);

    public enum State { DISABLED, STARTING, RUNNING, FAILED, STOPPED }

    /** For the admin status endpoint. */
    public record Status(State state, long published, Instant lastEventAt, String lastError) {}

    private final CdcProperties props;
    private final UserChangeMapper mapper;
    private final AnalyticsPublisher publisher;

    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
    private final AtomicLong published = new AtomicLong();
    private final AtomicReference<Instant> lastEventAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private ExecutorService executor;

    public UserCdcCapture(CdcProperties props, UserChangeMapper mapper, AnalyticsPublisher publisher) {
        this.props = props;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Override
    public void start() {
        if (!props.enabled()) {
            state.set(State.DISABLED);
            log.info("User CDC is disabled (app.cdc.enabled=false)");
            return;
        }
        try {
            Files.createDirectories(Path.of(props.offsetFile()).toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create directory for " + props.offsetFile(), e);
        }
        state.set(State.STARTING);
        engine = DebeziumEngine.create(Json.class)
                .using(debeziumConfig())
                .notifying(this::handleBatch)
                .using(new DebeziumEngine.ConnectorCallback() {
                    @Override
                    public void taskStarted() {
                        state.set(State.RUNNING);
                        log.info("User CDC running: streaming {} from {}:{}/{}", props.table(), props.host(),
                                props.port(), props.database());
                    }
                })
                .using((success, message, error) -> {
                    if (success) {
                        state.compareAndSet(State.RUNNING, State.STOPPED);
                    } else {
                        state.set(State.FAILED);
                        lastError.set(message);
                        log.error("User CDC stopped: {}", message, error);
                    }
                })
                .build();
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "user-cdc"));
        executor.execute(engine);
    }

    /** Publishes a batch of changes; marks each one processed only after Kafka acknowledged it. */
    private void handleBatch(List<ChangeEvent<String, String>> records,
                             DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer)
            throws InterruptedException {
        for (ChangeEvent<String, String> record : records) {
            mapper.map(record.value()).ifPresent(change -> {
                publisher.publishUserChange(change.userId(), change.value());
                published.incrementAndGet();
                lastEventAt.set(Instant.now());
                log.info("User {} changed (op={}) -> published", change.userId(), change.op());
            });
            committer.markProcessed(record);
        }
        committer.markBatchFinished();
    }

    private Properties debeziumConfig() {
        Properties p = new Properties();
        p.setProperty("name", "analytics-users-cdc");
        p.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        p.setProperty("plugin.name", "pgoutput");
        p.setProperty("publication.name", props.publication());
        p.setProperty("publication.autocreate.mode", "disabled");   // created by scripts/setup-postgres-cdc.sql
        p.setProperty("slot.name", props.slot());
        p.setProperty("database.hostname", props.host());
        p.setProperty("database.port", String.valueOf(props.port()));
        p.setProperty("database.user", props.user());
        p.setProperty("database.password", props.password());
        p.setProperty("database.dbname", props.database());
        p.setProperty("topic.prefix", "analytics");                 // required by Debezium; we publish ourselves
        p.setProperty("table.include.list", props.table());
        p.setProperty("snapshot.mode", "initial");                  // existing rows once, then changes
        p.setProperty("tombstones.on.delete", "false");
        // Plain JSON envelopes ({before, after, op, ...}) without the embedded schema
        p.setProperty("key.converter.schemas.enable", "false");
        p.setProperty("value.converter.schemas.enable", "false");
        p.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        p.setProperty("offset.storage.file.filename", props.offsetFile());
        p.setProperty("offset.flush.interval.ms", "1000");
        return p;
    }

    public Status status() {
        return new Status(state.get(), published.get(), lastEventAt.get(), lastError.get());
    }

    @Override
    public void stop() {
        if (engine != null) {
            try {
                engine.close();
            } catch (IOException e) {
                log.warn("Error closing the CDC engine", e);
            }
        }
        if (executor != null) {
            executor.shutdown();
        }
        if (state.get() != State.FAILED) {
            state.set(props.enabled() ? State.STOPPED : State.DISABLED);
        }
    }

    @Override
    public boolean isRunning() {
        State s = state.get();
        return s == State.STARTING || s == State.RUNNING;
    }

    /** Start after the Kafka infrastructure, stop before it. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
