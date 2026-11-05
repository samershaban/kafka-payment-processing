#!/bin/sh
# Submits the analytics Flink job (run by the flink-submit container). Safe to run on every `docker compose up`:
# does nothing if the job is already running.
#
#   RESTORE_FROM=s3://.../savepoint-xyz docker compose up flink-submit   # resume from a savepoint/checkpoint
set -eu

JM="${FLINK_JOBMANAGER:-flink-jobmanager:8081}"
JOB_NAME="${JOB_NAME:-analytics-lake}"
JAR=/jobs/analytics-flink.jar

if [ ! -f "$JAR" ]; then
  echo "No $JAR. Build it first: (cd analytics-flink && mvn package)"
  exit 1
fi

echo "Waiting for the Flink jobmanager at $JM..."
until flink list -r -m "$JM" >/dev/null 2>&1; do sleep 2; done

if flink list -r -m "$JM" 2>/dev/null | grep -q " : $JOB_NAME (RUNNING)"; then
  echo "$JOB_NAME is already running; nothing to do."
  exit 0
fi

if [ -n "${RESTORE_FROM:-}" ]; then
  echo "Submitting $JOB_NAME, restoring from $RESTORE_FROM"
  exec flink run -d -m "$JM" -s "$RESTORE_FROM" "$JAR" --job.name "$JOB_NAME"
fi

echo "Submitting $JOB_NAME"
exec flink run -d -m "$JM" "$JAR" --job.name "$JOB_NAME"
