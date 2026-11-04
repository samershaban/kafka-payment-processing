#!/bin/bash
# One-time migration: link orders created before user accounts existed to the seed users.
#
#   c-1 .. c-5     -> the fixed seed user UUIDs from V2__seed_users.sql (00000000-0000-4000-8000-00000000000N)
#   c-9, c-race    -> deleted (test orders created while testing idempotency, not real customers)
#   anything else  -> left alone and reported
#
# Only touches orders whose customerId still starts with "c-", so running it twice is harmless.
#
# Usage:  scripts/backfill-customer-ids.sh            # apply
#         scripts/backfill-customer-ids.sh --dry-run  # only show what would change
set -euo pipefail

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=true
fi

cql() {
  docker exec cassandra cqlsh -e "$1"
}

seed_user_for() {
  case "$1" in
    c-1) echo 00000000-0000-4000-8000-000000000001 ;;
    c-2) echo 00000000-0000-4000-8000-000000000002 ;;
    c-3) echo 00000000-0000-4000-8000-000000000003 ;;
    c-4) echo 00000000-0000-4000-8000-000000000004 ;;
    c-5) echo 00000000-0000-4000-8000-000000000005 ;;
    *) echo "" ;;
  esac
}

relinked=0
deleted=0
skipped=0

# COPY ... TO STDOUT prints clean CSV rows: <order id>,<customerId>
#  (cqlsh ends lines with \r\n; strip the \r or "c-1\r" would match nothing)
rows=$(cql "COPY orders.orders (id, customerid) TO STDOUT" | tr -d '\r' | grep -E '^[0-9a-f-]{36},' || true)

while IFS=, read -r order_id customer_id; do
  [ -z "$order_id" ] && continue
  case "$customer_id" in
    c-*) ;;
    *) continue ;;   # already a user UUID
  esac

  if [ "$customer_id" = "c-9" ] || [ "$customer_id" = "c-race" ]; then
    echo "delete  $order_id ($customer_id: test order)"
    $DRY_RUN || cql "DELETE FROM orders.orders WHERE id = '$order_id';"
    deleted=$((deleted + 1))
    continue
  fi

  user_id=$(seed_user_for "$customer_id")
  if [ -z "$user_id" ]; then
    echo "skip    $order_id ($customer_id: no matching seed user)"
    skipped=$((skipped + 1))
    continue
  fi

  echo "relink  $order_id $customer_id -> $user_id"
  $DRY_RUN || cql "UPDATE orders.orders SET customerid = '$user_id' WHERE id = '$order_id';"
  relinked=$((relinked + 1))
done <<EOF
$rows
EOF

if $DRY_RUN; then
  echo "Dry run: would relink $relinked, delete $deleted, skip $skipped. Nothing was changed."
else
  echo "Done: relinked $relinked, deleted $deleted, skipped $skipped."
fi
