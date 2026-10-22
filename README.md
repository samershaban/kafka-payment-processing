# Kafka Order Processing (Spring Boot)

A learning project: two Spring Boot microservices that talk through Kafka.

```
POST /orders ──► Order Service ──(orders.created)──► Payment Service
                       ▲                                   │
                       └──────(payments.processed)─────────┘
```

1. You `POST` an order. The Order Service saves it with status `CREATED` and publishes an `OrderCreatedEvent`.
2. The Payment Service consumes it, "charges" the card (about 10% fail at random) and publishes a `PaymentProcessedEvent`.
3. The Order Service consumes the result and updates the order to `PAID` or `PAYMENT_FAILED`.

## What you need

- Java 21
- Docker Desktop
- Maven (IntelliJ includes it, or use your own)

## Run it

**1. Start Kafka and Kafka UI**

```bash
docker compose up -d
```

Kafka UI is at http://localhost:8080. Use it to look at topics, partitions and messages.

**2. Start both services.** In IntelliJ, open the root `pom.xml` as a project, then run
`OrderServiceApplication` and `PaymentServiceApplication`. Or from a terminal:

```bash
cd order-service   && mvn spring-boot:run
cd payment-service && mvn spring-boot:run   # in a second terminal
```

The Order Service listens on port 8081. The Payment Service has no HTTP port; it only reacts to Kafka.

**3. Place an order**

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
docker compose down
```
