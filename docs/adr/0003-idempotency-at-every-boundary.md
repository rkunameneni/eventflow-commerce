# ADR 0003: Idempotency at every boundary

* Status: accepted
* Scope: order API, all Kafka consumers, payment provider integration

## Context

Every hop in the system can deliver the same request twice: mobile clients retry POSTs after a
timeout, Kafka redelivers after a rebalance or a crash before the offset commit, the outbox relay
can republish a batch, and our own retries re-send HTTP calls to the payment provider. Without
protection each of these can create a duplicate order, reserve stock twice or - worst - charge a
customer twice.

## Decision

Make every boundary idempotent, each with the mechanism that fits it:

| Boundary | Mechanism |
|---|---|
| `POST /api/v1/orders` | Required `Idempotency-Key` header. The key is stored with the created order id and a SHA-256 fingerprint of the normalised request body, in the same transaction as the order. A retry with the same key and body returns the original order (`200`, `Idempotent-Replayed: true`); the same key with a different body is rejected with `422`. Keys are scoped per customer, and the table's primary key makes concurrent duplicates fail instead of racing. |
| Kafka consumers | `processed_events (event_id, consumer)` marker inserted with `ON CONFLICT DO NOTHING` inside the handler's transaction. If the handler fails, the marker rolls back with it and the redelivery is processed; if it succeeded, the redelivery is skipped. |
| Aggregates | State transitions ignore signals that no longer apply (a late `PaymentFailed` after confirmation changes nothing), which also covers duplicates that reach the domain. |
| Payment provider | The payment row is created before the charge and its id is sent as the provider's `Idempotency-Key`. A redelivered event re-sends the same key, so the provider answers with the original result instead of charging again. The provider call happens between two short transactions, never inside one. |
| Dead-letter replay | Replayed messages are the original events with their original ids, so the consumer-side marker still applies. |

Notifications are the one deliberate exception: a message can go out twice if the transaction
fails after sending, because a duplicate e-mail is preferable to a lost one.

## Consequences

* Retrying is always safe, which is what makes the aggressive retry policies (HTTP retries,
  consumer back-off, DLT replay) acceptable.
* Each service carries a small `processed_events` table; old rows can be pruned once they are
  older than the maximum redelivery window.
* The API contract requires clients to generate keys (a UUID per checkout attempt); this is
  documented in the OpenAPI description of the endpoint.
