# ADR 0002: Choreographed saga with a transactional outbox

* Status: accepted
* Scope: `order-service`, `inventory-service`, `payment-service`, `notification-service`, `service-support`

## Context

Placing an order touches three services with their own databases: stock must be reserved, the
customer charged, and the order confirmed - or everything undone. A distributed transaction (2PC)
across Postgres databases and Kafka is not available in practice and would couple the services'
availability.

Two problems have to be solved:

1. **Coordination:** who decides the next step, and how is a failure compensated?
2. **Atomic publish:** a service must never commit a state change without publishing the event
   (or publish an event for a change that rolled back). Writing to the database and then calling
   `KafkaTemplate.send()` fails in both directions under crashes and broker outages.

## Decision

**Choreography.** Each participant subscribes to the events it cares about and publishes its own
outcome (see the sequence diagram in [architecture.md](../architecture.md)). Compensation is
event-driven too: `OrderCancelled` makes inventory release its reservation. The flow has four
participants and one compensation, which choreography expresses without a central coordinator; if
the process grows (shipping, fraud checks, partial refunds), an orchestrator should be
reconsidered because implicit flows get hard to follow.

**Transactional outbox, in every producing service.** Services never send to Kafka from business
code. `OutboxWriter.append()` inserts the event into `outbox_events` inside the business
transaction (it refuses to run without one). `OutboxRelay` polls the table, publishes pending rows
in insertion order and marks them published. Rows are claimed with `FOR UPDATE SKIP LOCKED`, so
any number of replicas can relay without coordination. The outbox lives in the shared
`service-support` starter; each service creates the table in its own Flyway migration.

**Keys and ordering.** Every event is keyed by order id, so all events of an order share a
partition and are consumed in order by each consumer group.

## Consequences

* A state change and its event are atomic; a Kafka outage delays events but loses none (the relay
  retries, and `eventflow.outbox.relay.failures` makes the backlog visible).
* Delivery is at-least-once (a crash between broker ack and marking the row publishes it again),
  so consumers must be idempotent - see [ADR 0003](0003-idempotency-at-every-boundary.md).
* End-to-end latency includes the relay poll interval (250 ms by default). Change data capture
  (Debezium) would remove polling at the cost of running Kafka Connect; not needed at this scale.
* Events from different topics can arrive in any order relative to each other (e.g. the order
  service may see `PaymentCompleted` before `InventoryReserved`). State transitions are therefore
  guarded in the aggregates rather than assuming a sequence.
* Published rows are purged after a retention period to keep the table small.
