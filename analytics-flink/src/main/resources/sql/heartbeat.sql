-- Placeholder pipeline: proves the job deploys and runs. Replaced by the real pipelines.
CREATE TEMPORARY TABLE heartbeat_source (n BIGINT) WITH ('connector' = 'datagen', 'rows-per-second' = '1');

CREATE TEMPORARY TABLE heartbeat_sink (n BIGINT) WITH ('connector' = 'blackhole');

INSERT INTO heartbeat_sink SELECT n FROM heartbeat_source;
