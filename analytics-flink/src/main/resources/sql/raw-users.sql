-- Raw layer for users: every change event of analytics.users.raw (Debezium envelope), as Parquet in S3.
-- Append-only: one row per change. before/after stay as nested structs (the row before and after the change;
-- before is null for inserts and snapshot reads, after is null for deletes).
-- op: r = initial snapshot read, c = insert, u = update, d = delete.
-- Files: ${lake}/raw/users/dt=YYYY-MM-DD/hour=HH/part-*  (UTC, by changedAt), committed on checkpoint.

CREATE TEMPORARY TABLE users_raw (
    eventId        STRING,
    op             STRING,
    ts_ms          BIGINT,
    `before`       ROW<id STRING, name STRING, email STRING, company STRING, address STRING, timezone STRING, enrolled BOOLEAN>,
    `after`        ROW<id STRING, name STRING, email STRING, company STRING, address STRING, timezone STRING, enrolled BOOLEAN>,
    `source`       ROW<ts_ms BIGINT, lsn BIGINT, txId BIGINT, snapshot STRING>,
    kafkaPartition INT              METADATA FROM 'partition' VIRTUAL,
    kafkaOffset    BIGINT           METADATA FROM 'offset' VIRTUAL,
    kafkaTimestamp TIMESTAMP_LTZ(3) METADATA FROM 'timestamp' VIRTUAL
) WITH (
    'connector' = 'kafka',
    'topic' = '${topic.users}',
    'properties.bootstrap.servers' = '${kafka.bootstrap}',
    'properties.group.id' = '${kafka.group}',
    'scan.startup.mode' = 'group-offsets',
    'properties.auto.offset.reset' = 'earliest',
    'format' = 'json'
);

CREATE TEMPORARY TABLE lake_raw_users (
    eventId        STRING,
    op             STRING,
    userId         STRING,
    changedAt      TIMESTAMP_LTZ(3),   -- when Postgres committed the change (snapshot reads: when it was read)
    capturedAt     TIMESTAMP_LTZ(3),   -- when Debezium processed it
    lsn            BIGINT,
    txId           BIGINT,
    snapshot       STRING,
    `before`       ROW<id STRING, name STRING, email STRING, company STRING, address STRING, timezone STRING, enrolled BOOLEAN>,
    `after`        ROW<id STRING, name STRING, email STRING, company STRING, address STRING, timezone STRING, enrolled BOOLEAN>,
    kafkaPartition INT,
    kafkaOffset    BIGINT,
    kafkaTimestamp TIMESTAMP_LTZ(3),
    dt             STRING,
    `hour`         STRING
) PARTITIONED BY (dt, `hour`) WITH (
    'connector' = 'filesystem',
    'path' = '${lake}/raw/users',
    'format' = 'parquet',
    'parquet.compression' = 'SNAPPY',
    'parquet.write.int64.timestamp' = 'true',
    'parquet.timestamp.time.unit' = 'millis'
);

INSERT INTO lake_raw_users
SELECT
    eventId, op,
    COALESCE(`after`.id, `before`.id),
    TO_TIMESTAMP_LTZ(`source`.ts_ms, 3),
    TO_TIMESTAMP_LTZ(ts_ms, 3),
    `source`.lsn, `source`.txId, `source`.snapshot,
    `before`, `after`,
    kafkaPartition, kafkaOffset, kafkaTimestamp,
    DATE_FORMAT(TO_TIMESTAMP_LTZ(`source`.ts_ms, 3), 'yyyy-MM-dd'),
    DATE_FORMAT(TO_TIMESTAMP_LTZ(`source`.ts_ms, 3), 'HH')
FROM users_raw;
