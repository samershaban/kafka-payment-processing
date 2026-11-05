package com.example.analytics.flink;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptsTest {

    @Test
    void split_dropsCommentsAndTrailingSemicolons() {
        String script = """
                -- a comment
                CREATE TABLE a (x INT)
                  WITH ('connector' = 'blackhole');

                INSERT INTO a SELECT 1;
                """;

        assertEquals(List.of("CREATE TABLE a (x INT)\n  WITH ('connector' = 'blackhole')", "INSERT INTO a SELECT 1"),
                SqlScripts.split(script));
    }

    @Test
    void substitute_fillsPlaceholders_andFailsOnUnknownOnes() {
        assertEquals("'s3://lake/raw'", SqlScripts.substitute("'${lake}/raw'", Map.of("lake", "s3://lake")));
        assertThrows(IllegalArgumentException.class, () -> SqlScripts.substitute("${nope}", Map.of()));
    }

    @Test
    void variables_haveDefaults_thatArgumentsOverride() {
        Map<String, String> vars = SqlScripts.variables(new String[] {"--topic.orders", "test.orders"});

        assertEquals("test.orders", vars.get("topic.orders"));
        assertEquals("kafka:29092", vars.get("kafka.bootstrap"));
        assertThrows(IllegalArgumentException.class, () -> SqlScripts.variables(new String[] {"--dangling"}));
    }

    @Test
    void load_readsEveryFileInThePipelineIndex() {
        List<String> statements = SqlScripts.load(SqlScripts.variables(new String[0]));

        assertFalse(statements.isEmpty());
        assertTrue(statements.stream().anyMatch(SqlScripts::isInsert));
    }
}
