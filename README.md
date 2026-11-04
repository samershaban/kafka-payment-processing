# Kafka Order Processing (Spring Boot)

A learning project: two Spring Boot microservices that talk through Kafka, with orders stored in Cassandra
and a small React UI for placing them.

```
POST /orders ──► Order Service ──(orders.created)──► Payment Service
                       ▲                                   │
                       └──────(payments.processed)─────────┘
```

1. You `POST` an order (or use the UI). The Order Service saves it to Cassandra with status `CREATED` and publishes an `OrderCreatedEvent`.
2. The Payment Service consumes it, "charges" the card (about 10% fail at random) and publishes a `PaymentProcessedEvent`.
3. The Order Service consumes the result and updates the order to `PAID` or `PAYMENT_FAILED`.

## What you need

- Java 21
- Docker Desktop
- Maven (IntelliJ includes it, or use your own)
- Node 20+ (for the UI)

## Run it

**1. Start Kafka, Kafka UI and Cassandra**

```bash
docker compose up -d
```

Cassandra takes 30-60 seconds to start. `docker compose up -d` waits for it, then the one-shot `cassandra-init`
container creates the `orders` keyspace and exits. Check with `docker compose ps -a`: `cassandra` should be
`healthy` and `cassandra-init` `Exited (0)`. The Order Service won't start until the keyspace exists.

Kafka UI is at http://localhost:8080. Use it to look at topics, partitions and messages.

**2. Start both services.** In IntelliJ, open the root `pom.xml` as a project, then run
`OrderServiceApplication` and `PaymentServiceApplication`. Or from a terminal:

```bash
cd order-service   && mvn spring-boot:run
cd payment-service && mvn spring-boot:run   # in a second terminal
```

The Order Service listens on port 8081 and creates the `orders` table on startup. The Payment Service has no
HTTP port; it only reacts to Kafka.

**3. Place an order from the UI**

```bash
cd order-ui && npm install && npm run dev
```

Open http://localhost:5175. Submit the form and watch the order go from `CREATED` to `PAID` or
`PAYMENT_FAILED`. The Vite dev server forwards `/orders` to the Order Service on 8081, so no CORS setup is needed.

**Or place one with curl**

```bash
curl -X POST http://localhost:8081/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"c-1","item":"Margherita pizza","quantity":2,"totalPrice":24.50}'
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
  -d '{"customerId":"c-1","item":"Ramen","quantity":1,"totalPrice":12}'   # 201 Created
# run the same command again                                              # 200 OK, same order id
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

**4. Look at the data in Cassandra**

```bash
docker exec -it cassandra cqlsh -e "SELECT id, customerid, status, statusreason FROM orders.orders;"
```

Or open an interactive shell with `docker exec -it cassandra cqlsh` and try `DESCRIBE TABLE orders.orders;`.
Data is kept in the `cassandra-data` Docker volume, so it survives restarts of the services and of the containers.

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
