package com.example.analytics.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserChangeMapperTest {

    private final ObjectMapper json = new ObjectMapper();
    private final UserChangeMapper mapper = new UserChangeMapper(json);

    private static final String UPDATE = """
            {"before":{"id":"u1","name":"Maya","enrolled":false},
             "after":{"id":"u1","name":"Maya","enrolled":true},
             "source":{"lsn":12345,"txId":77,"table":"users"},"op":"u","ts_ms":1793813612000}""";

    private static final String DELETE = """
            {"before":{"id":"u2","name":"Tom"},"after":null,
             "source":{"lsn":12399,"txId":78},"op":"d","ts_ms":1793813613000}""";

    @Test
    void update_isKeyedByUserId_keepsTheEnvelope_andAddsAnEventId() throws Exception {
        var change = mapper.map(UPDATE).orElseThrow();

        assertThat(change.userId()).isEqualTo("u1");
        assertThat(change.op()).isEqualTo("u");
        var value = json.readTree(change.value());
        assertThat(value.path("after").path("enrolled").asBoolean()).isTrue();
        assertThat(value.path("before").path("enrolled").asBoolean()).isFalse();
        assertThat(value.path("eventId").asText()).isNotBlank();
    }

    @Test
    void delete_usesTheBeforeRowForTheKey() {
        var change = mapper.map(DELETE).orElseThrow();
        assertThat(change.userId()).isEqualTo("u2");
        assertThat(change.op()).isEqualTo("d");
    }

    @Test
    void sameChangeDeliveredTwice_getsTheSameEventId() throws Exception {
        String first = json.readTree(mapper.map(UPDATE).orElseThrow().value()).path("eventId").asText();
        String second = json.readTree(mapper.map(UPDATE).orElseThrow().value()).path("eventId").asText();
        String other = json.readTree(mapper.map(DELETE).orElseThrow().value()).path("eventId").asText();

        assertThat(first).isEqualTo(second).isNotEqualTo(other);
    }

    @Test
    void tombstone_isSkipped() {
        assertThat(mapper.map(null)).isEmpty();
    }

    @Test
    void garbage_isRejected() {
        assertThatThrownBy(() -> mapper.map("not json")).isInstanceOf(IllegalArgumentException.class);
    }
}
