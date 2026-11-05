-- Raw layer for orders: every record of analytics.orders.raw, unchanged, as Parquet in S3.
-- The nested "order" object is flattened into columns; Kafka partition/offset/timestamp are kept for tracing.
-- Files: ${lake}/raw/orders/dt=YYYY-MM-DD/hour=HH/part-*  (UTC, by the event's occurredAt), committed on checkpoint.

CREATE TEMPORARY TABLE orders_raw (
    eventId        STRING,
    kind           STRING,
    occurredAt     TIMESTAMP_LTZ(3),
    `order`        ROW<
                       id           STRING,
                       customerId   STRING,
                       item         STRING,
                       quantity     INT,
                       totalPrice   DECIMAL(12, 2),
                       status       STRING,
                       statusReason STRING,
                       createdAt    TIMESTAMP_LTZ(3),
                       updatedAt    TIMESTAMP_LTZ(3)>,
    kafkaPartition INT              METADATA FROM 'partition' VIRTUAL,
    kafkaOffset    BIGINT           METADATA FROM 'offset' VIRTUAL,
    kafkaTimestamp TIMESTAMP_LTZ(3) METADATA FROM 'timestamp' VIRTUAL
) WITH (
    'connector' = 'kafka',
    'topic' = '${topic.orders}',
    'properties.bootstrap.servers' = '${kafka.bootstrap}',
    'properties.group.id' = '${kafka.group}',
    -- First start: from the beginning. After that: from the offsets in the checkpoint (or committed to Kafka).
    'scan.startup.mode' = 'group-offsets',
    'properties.auto.offset.reset' = 'earliest',
    'format' = 'json',
    'json.timestamp-format.standard' = 'ISO-8601'
);

CREATE TEMPORARY TABLE lake_raw_orders (
    eventId        STRING,
    kind           STRING,
    occurredAt     TIMESTAMP_LTZ(3),
    orderId        STRING,
    customerId     STRING,
    item           STRING,
    quantity       INT,
    totalPrice     DECIMAL(12, 2),
    status         STRING,
    statusReason   STRING,
    createdAt      TIMESTAMP_LTZ(3),
    updatedAt      TIMESTAMP_LTZ(3),
    kafkaPartition INT,
    kafkaOffset    BIGINT,
    kafkaTimestamp TIMESTAMP_LTZ(3),
    dt             STRING,
    `hour`         STRING
) PARTITIONED BY (dt, `hour`) WITH (
    'connector' = 'filesystem',
    'path' = '${lake}/raw/orders',
    'format' = 'parquet',
    'parquet.compression' = 'SNAPPY',
    -- Standard INT64 timestamps (millis, UTC) instead of the legacy INT96 default
    'parquet.write.int64.timestamp' = 'true',
    'parquet.timestamp.time.unit' = 'millis'
);

INSERT INTO lake_raw_orders
SELECT
    eventId, kind, occurredAt,
    `order`.id, `order`.customerId, `order`.item, `order`.quantity, `order`.totalPrice,
    `order`.status, `order`.statusReason, `order`.createdAt, `order`.updatedAt,
    kafkaPartition, kafkaOffset, kafkaTimestamp,
    DATE_FORMAT(occurredAt, 'yyyy-MM-dd'),
    DATE_FORMAT(occurredAt, 'HH')
FROM orders_raw;
