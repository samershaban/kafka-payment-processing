package com.example.analytics.flink;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the job's SQL from the classpath: {@code sql/pipeline.txt} lists the files to run, in order. Each file holds
 * statements ending in {@code ;} at the end of a line; {@code --} comment lines are dropped and {@code ${name}}
 * placeholders are replaced from the job's variables.
 */
final class SqlScripts {

    static final String INDEX = "sql/pipeline.txt";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    private SqlScripts() {}

    /** Defaults match docker-compose; override any of them with {@code --name value} job arguments. */
    static Map<String, String> variables(String[] args) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("job.name", "analytics-lake");
        vars.put("checkpoint.interval", "10s");
        vars.put("parallelism", "4");   // = slots of one taskmanager; 12 (= partitions) with 3 taskmanagers
        vars.put("kafka.bootstrap", "kafka:29092");
        vars.put("kafka.group", "analytics-flink");
        vars.put("topic.orders", "analytics.orders.raw");
        vars.put("topic.users", "analytics.users.raw");
        vars.put("lake", "s3://kafka-orders-lake");
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value pairs, got: " + String.join(" ", args));
            }
            vars.put(args[i].substring(2), args[++i]);
        }
        return vars;
    }

    /** All statements of all files listed in the index, in order, with placeholders filled in. */
    static List<String> load(Map<String, String> vars) {
        List<String> statements = new ArrayList<>();
        for (String line : read(INDEX).split("\n")) {
            String file = line.strip();
            if (!file.isEmpty() && !file.startsWith("#")) {
                statements.addAll(split(substitute(read("sql/" + file), vars)));
            }
        }
        return statements;
    }

    static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            if (line.strip().startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (line.stripTrailing().endsWith(";")) {
                add(statements, current);
            }
        }
        add(statements, current);
        return statements;
    }

    static String substitute(String sql, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            if (value == null) {
                throw new IllegalArgumentException("No value for ${" + m.group(1) + "}");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        return m.appendTail(out).toString();
    }

    static boolean isInsert(String statement) {
        return statement.stripLeading().regionMatches(true, 0, "INSERT", 0, 6);
    }

    private static void add(List<String> statements, StringBuilder current) {
        String statement = current.toString().strip();
        if (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).strip();
        }
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

    private static String read(String resource) {
        try (InputStream in = SqlScripts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
