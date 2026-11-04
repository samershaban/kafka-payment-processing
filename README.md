# Kafka Order Processing (Spring Boot)

A learning project: two Spring Boot microservices that talk through Kafka, with orders stored in Cassandra,
user accounts in Postgres, and a small React UI for placing orders.

```
POST /orders ──► Order Service ──(orders.created)──► Payment Service
                       ▲                                   │
                       └──────(payments.processed)─────────┘
```

1. You `POST` an order for an existing user (or use the UI). The Order Service saves it to Cassandra with status `CREATED` and publishes an `OrderCreatedEvent`.
2. The Payment Service consumes it, "charges" the card (a configurable share fail at random, `app.payment.failure-rate`) and publishes a `PaymentProcessedEvent`.
3. The Order Service consumes the result and updates the order to `PAID` or `PAYMENT_FAILED`.

## What you need

- Java 21
- Docker Desktop
- Maven (IntelliJ includes it, or use your own)
- Node 20+ (for the UI)
- PostgreSQL 15+ and pgAdmin 4 installed on your computer (for user accounts). The
  [EnterpriseDB installer](https://www.postgresql.org/download/) includes both.

## Run it

**1. Set up Postgres (once)**

The Order Service stores users in a Postgres on your computer (not in Docker). Create a login and a database
for it, both called `orders`. Either way below works; you only need the superuser (`postgres`) password you
chose when installing Postgres.

*In pgAdmin:* open pgAdmin 4, expand **Servers → PostgreSQL 15** (enter the `postgres` password), right-click the
server → **Query Tool**, paste the SQL below and press **Execute** (F5).

*Or in a terminal:*

```bash
psql -h localhost -U postgres
```

```sql
CREATE ROLE orders WITH LOGIN PASSWORD 'orders';
CREATE DATABASE orders OWNER orders;
```

That's all: you don't create any tables. When the Order Service starts, Flyway creates the `users` table and
five seed users (`order-service/src/main/resources/db/migration/`).

The connection settings are in `order-service/src/main/resources/application.yml`
(`localhost:5432`, database `orders`, user `orders`, password `orders`). If yours differ, set environment
variables instead of editing the file, e.g. `DB_PASSWORD=secret` (also `DB_USERNAME`, and `DB_URL` for the
host, port or database).

**2. Start Kafka, Kafka UI and Cassandra**

```bash
docker compose up -d
```

Cassandra takes 30-60 seconds to start. `docker compose up -d` waits for it, then the one-shot `cassandra-init`
container creates the `orders` keyspace and exits. Check with `docker compose ps -a`: `cassandra` should be
`healthy` and `cassandra-init` `Exited (0)`. The Order Service won't start until the keyspace exists.

Kafka UI is at http://localhost:8080. Use it to look at topics, partitions and messages. Grafana (Cassandra UI) is at http://localhost:3001.

`docker compose up -d` also runs a one-shot `postgres-seed` container (`postgres/random-users.sql`). It adds
**5 random users** to your local Postgres, but only if there are no users yet besides the 5 fixed seed users,
so it adds them once and does nothing on later starts. Check what it did with `docker logs postgres-seed`.
On a brand-new database it also waits for the Order Service to have started once (Flyway creates the `users`
table and the seed users), so run `docker compose up -d` again after that, or run just the seed job:

```bash
docker compose run --rm postgres-seed
```

**3. Start both services.** In IntelliJ, open the root `pom.xml` as a project, then run
`OrderServiceApplication` and `PaymentServiceApplication`. Or from a terminal:

```bash
cd order-service   && mvn spring-boot:run
cd payment-service && mvn spring-boot:run   # in a second terminal
```

The Order Service listens on port 8081. On startup it creates the Cassandra `orders` table and its
`customerid` index, and Flyway creates the Postgres `users` table and five seed users
(`db/migration/V1__create_users.sql`, `V2__seed_users.sql`). The Payment Service has no HTTP port; it only reacts to Kafka.

**Upgrading from before user accounts existed?** Orders used to have customer IDs like `c-1`. Run this once to
link them to the seed users (preview first with `--dry-run`):

```bash
scripts/backfill-customer-ids.sh --dry-run
scripts/backfill-customer-ids.sh
```

**4. Place an order from the UI**

```bash
cd order-ui && npm install && npm run dev
```

Open http://localhost:5175. Submit the form and watch the order go from `CREATED` to `PAID` or
`PAYMENT_FAILED`. **Random order** and **Place random order** pick one of the existing users as the customer.
The **Users** page (http://localhost:5175/users, in the top navigation) lists every user with their details and
lets you switch **Enrolled** on or off; the change is saved straight away (`PUT /users/{id}`).

The UI calls the API under `/api` (e.g. `/api/users`). The Vite dev server strips `/api` and forwards to the Order
Service on 8081, so no CORS setup is needed, and page URLs like `/users` don't clash with API URLs.

The list updates live, without polling: the page subscribes to `GET /orders/stream` (Server-Sent Events), and
the Order Service pushes an `order` event whenever an order is created or its payment result arrives from Kafka.
That includes orders placed with curl or in another tab. The dot next to the Orders title shows **Live** while connected;
if the connection drops, the browser reconnects by itself and the page reloads the list. Watch the raw stream with:

```bash
curl -N http://localhost:8081/orders/stream    # or via the UI: http://localhost:5175/api/orders/stream
```

**Or place one with curl.** `customerId` must be an existing user's ID (otherwise `422 Unknown customer`).
`00000000-0000-4000-8000-000000000001` is a seed user that always exists:

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"00000000-0000-4000-8000-000000000001","item":"Margherita pizza","quantity":2,"totalPrice":24.50}'
```

The response shows `"status": "CREATED"`. About a second later:

```bash
curl http://localhost:8081/orders
```

The order should now be `PAID` (or `PAYMENT_FAILED` with a reason). Watch both services' logs to see
each event's partition and offset.

**Safe retries with an idempotency key.** Send an `Idempotency-Key` header (a fresh UUID per order you mean to
place) and repeating the request can't create a duplicate:

```bash
KEY=$(uuidgen)
curl -i -X POST http://localhost:8081/orders -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d '{"customerId":"00000000-0000-4000-8000-000000000001","item":"Ramen","quantity":1,"totalPrice":12}'
# 201 Created. Run the same command again: 200 OK, same order id.
```

| Request | Result |
|---|---|
| New key | `201`, order created |
| Same key, same body | `200`, the original order (nothing saved or published again) |
| Same key, different body | `422` |
| Same key while the first request is still running | `409`, retry shortly |
| No header | `201`, always a new order |

Keys are stored in `orders.idempotency_keys` and expire after 24 hours. The UI sends a key automatically and
only reuses it when retrying the same order after a failed request.

**5. Manage users**

Users have `id` (UUID), `name`, `email` (unique, stored lowercased), `company`, `address`, `timezone`
(IANA name such as `Europe/London`, default `UTC`) and `enrolled`.

```bash
curl http://localhost:8081/users                                   # all users (?enrolled=true to filter)
curl -i -X POST http://localhost:8081/users -H "Content-Type: application/json" \
  -d '{"name":"Sam Lee","email":"sam@example.com","company":"Acme","address":"1 Main St","timezone":"America/New_York"}'
curl http://localhost:8081/users/00000000-0000-4000-8000-000000000001/orders   # a user's orders, newest first
```

| Endpoint | Responses |
|---|---|
| `POST /users` | `201`; `400` invalid field; `409` email already in use |
| `GET /users`, `GET /users/{id}` | `200`; `404` unknown user |
| `PUT /users/{id}` (all fields; `timezone` and `enrolled` required) | `200`; `400`; `404`; `409` |
| `DELETE /users/{id}` | `204`; `404`; `409` if the user has orders |
| `GET /users/{id}/orders` | `200`; `404` |

**6. Look at the data**

Postgres (users), in pgAdmin:

1. **Servers → PostgreSQL 15 → Databases → orders → Schemas → public → Tables → users**.
2. Right-click `users` → **View/Edit Data → All Rows** to see and edit rows in a grid.
3. For your own queries, right-click the `orders` database → **Query Tool**, for example:

```sql
SELECT name, email, company, timezone, enrolled FROM users ORDER BY name;
SELECT count(*) FILTER (WHERE enrolled) AS enrolled, count(*) AS total FROM users;
```

The `flyway_schema_history` table next to `users` records which migrations have run; don't edit it.

Or from a terminal: `psql -h localhost -U orders -d orders -c "SELECT * FROM users;"` (password `orders`).

Cassandra (orders), in **Grafana** at http://localhost:3001 (started by `docker compose up -d`, no login):

- **Dashboards → Kafka Orders → Orders (Cassandra)**: order count, paid revenue, average order, orders by
  status and by item, and a table of all orders. It refreshes every 10 seconds, so new orders from the UI show up.
- **Explore** (compass icon), data source **Cassandra**: switch the query editor to **Raw CQL** to run your own
  queries, e.g.

  ```sql
  SELECT id, customerid, item, status, CAST(totalprice AS double) AS total, createdat FROM orders.orders
  ```

  Two quirks of the Cassandra plugin: wrap `totalprice` in `CAST(... AS double)` (it can't read the `decimal`
  type), and it returns one result per row, so in panels add the **Merge** transformation to get a single table.
- The connection and dashboard are set up by files in `grafana/` (provisioning). You can edit the dashboard in the
  UI; to keep changes after `docker compose down -v`, use **Share → Export** and save the JSON over
  `grafana/dashboards/orders.json`.

Or from a terminal:

```bash
docker exec -it cassandra cqlsh -e "SELECT id, customerid, status, statusreason FROM orders.orders;"
```

Or open an interactive shell with `docker exec -it cassandra cqlsh` and try `DESCRIBE TABLE orders.orders;`.
Cassandra data is kept in the `cassandra-data` Docker volume, so it survives restarts of the services and of the
containers. Users are in your local Postgres, independent of Docker.

## Analytics setup (in progress)

Preparation for a real-time analytics pipeline (capture → Kafka → Flink → S3). Nothing uses these yet.

**Postgres change data capture (once).** Lets the future analytics service stream every change to `users`.
Needs the `postgres` superuser password and a Postgres restart, which briefly disconnects all databases on it:

```bash
psql -h localhost -U postgres -d orders -f scripts/setup-postgres-cdc.sql   # sets wal_level, role, publication
sudo launchctl kickstart -k system/postgresql-15                            # restart (EnterpriseDB on macOS)
psql -h localhost -U postgres -d orders -f scripts/setup-postgres-cdc.sql   # again: should end with "ready"
```

## Tests

```bash
cd order-service && mvn test
```

Unit tests for the services (Mockito) and the users web layer (`@WebMvcTest`). They don't need Docker.

## Things to try next

- **Consumer groups:** start a second Payment Service instance (in IntelliJ, edit the run configuration and
  allow multiple instances). Send ~10 orders and watch the logs: the 3 partitions get split between the two
  instances. Stop one and watch Kafka rebalance.
- **Replay:** stop the Payment Service, place a few orders, then start it again. It picks up where it left off.
- **Inspect a message** in Kafka UI and notice the key is the order ID.
- **Break something:** send a message with bad JSON to `orders.created` from Kafka UI. What happens?
  (That's the motivation for stage 3: error handlers and dead-letter topics.)

## Roadmap

1. ✅ Producer/consumer basics, JSON events
2. Consumer groups and partition keys (see "Things to try")
3. Error handling: retries, `DefaultErrorHandler`, dead-letter topic
4. Inventory Service plus a saga with a compensating `RefundRequested` event
5. Reliability: idempotent consumers, transactional outbox, manual offset commits
6. Stretch: Kafka Streams, Avro + Schema Registry, Testcontainers tests

## Stop everything

```bash
docker compose down        # keeps Cassandra data
docker compose down -v     # also deletes the Cassandra volume (all orders)
```
