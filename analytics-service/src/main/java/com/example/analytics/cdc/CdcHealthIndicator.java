package com.example.analytics.cdc;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Adds "cdc" to /actuator/health: UP while streaming, DOWN with the reason if the Debezium engine stopped with an
 * error (e.g. Postgres not prepared with scripts/setup-postgres-cdc.sql), UNKNOWN while starting.
 */
@Component("cdc")
public class CdcHealthIndicator implements HealthIndicator {

    private final UserCdcCapture capture;

    public CdcHealthIndicator(UserCdcCapture capture) {
        this.capture = capture;
    }

    @Override
    public Health health() {
        UserCdcCapture.Status s = capture.status();
        Health.Builder health = switch (s.state()) {
            case RUNNING -> Health.up();
            case FAILED -> Health.down().withDetail("error", String.valueOf(s.lastError()));
            case STARTING -> Health.unknown();
            case DISABLED, STOPPED -> Health.outOfService();
        };
        return health.withDetail("state", s.state()).withDetail("published", s.published()).build();
    }
}
