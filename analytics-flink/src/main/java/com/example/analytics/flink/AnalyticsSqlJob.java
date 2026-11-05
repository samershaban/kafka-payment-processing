package com.example.analytics.flink;

import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.TableEnvironment;

import java.time.ZoneOffset;
import java.util.Map;

/**
 * The analytics Flink job: runs the SQL files listed in sql/pipeline.txt. Table definitions run immediately; all
 * INSERTs are combined into a single Flink job, so every pipeline shares one set of checkpoints.
 *
 * The checkpoint interval is set here; the state backend, checkpoint location and S3 settings come from the
 * cluster config (docker-compose).
 */
public final class AnalyticsSqlJob {

    private AnalyticsSqlJob() {}

    public static void main(String[] args) {
        Map<String, String> vars = SqlScripts.variables(args);
        TableEnvironment tables = TableEnvironment.create(EnvironmentSettings.inStreamingMode());
        tables.getConfig().set(PipelineOptions.NAME, vars.get("job.name"));
        // dt=/hour= folders and DATE_FORMAT on timestamps use UTC, whatever the machine's zone
        tables.getConfig().setLocalTimeZone(ZoneOffset.UTC);
        // Set here, not only in the cluster config: job-level settings come from the submitting client. The S3
        // file sinks only commit files on checkpoints, so checkpointing must be on.
        tables.getConfig().set("execution.checkpointing.interval", vars.get("checkpoint.interval"));
        tables.getConfig().set("parallelism.default", vars.get("parallelism"));
        // Keep the last checkpoint when the job is cancelled, so it can be resumed with RESTORE_FROM
        tables.getConfig().set("execution.checkpointing.externalized-checkpoint-retention", "RETAIN_ON_CANCELLATION");

        StatementSet inserts = tables.createStatementSet();
        for (String statement : SqlScripts.load(vars)) {
            if (SqlScripts.isInsert(statement)) {
                inserts.addInsertSql(statement);
            } else {
                tables.executeSql(statement);
            }
        }
        inserts.execute();
    }
}
