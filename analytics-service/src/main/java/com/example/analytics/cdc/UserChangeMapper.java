package com.example.analytics.cdc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns one Debezium change event for the users table into the record published on analytics.users.raw.
 *
 * The value stays in Debezium's standard envelope ({before, after, op, ts_ms, source}), which Flink's
 * debezium-json format reads directly; an eventId is added for duplicate detection. op is c (insert), u (update),
 * d (delete) or r (row read during the initial snapshot).
 */
@Component
public class UserChangeMapper {

    private final ObjectMapper json;

    public UserChangeMapper(ObjectMapper json) {
        this.json = json;
    }

    /** Key (user id) and value (JSON) of the record to publish. */
    public record UserChange(String userId, String op, String value) {}

    /** Empty for events with no row (Kafka tombstones that follow deletes). */
    public Optional<UserChange> map(String debeziumValue) {
        if (debeziumValue == null) {
            return Optional.empty();
        }
        try {
            ObjectNode envelope = (ObjectNode) json.readTree(debeziumValue);
            JsonNode row = envelope.path("after").isObject() ? envelope.path("after") : envelope.path("before");
            if (!row.isObject() || row.path("id").isMissingNode()) {
                return Optional.empty();
            }
            String userId = row.path("id").asText();
            String op = envelope.path("op").asText();
            envelope.put("eventId", eventId(envelope.path("source"), userId, op));
            return Optional.of(new UserChange(userId, op, json.writeValueAsString(envelope)));
        } catch (JsonProcessingException | ClassCastException e) {
            throw new IllegalArgumentException("Not a Debezium change event: " + debeziumValue, e);
        }
    }

    /**
     * Deterministic id from the change's position in the Postgres WAL (lsn) plus the row and operation: if Debezium
     * re-delivers the same change after a restart, it gets the same eventId and Flink drops the repeat.
     */
    static String eventId(JsonNode source, String userId, String op) {
        String name = "users:" + source.path("lsn").asText() + ":" + source.path("txId").asText() + ":" + userId + ":" + op;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
