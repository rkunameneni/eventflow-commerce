# ADR 0001: Decompose the monolith with the strangler-fig pattern

* Status: accepted
* Scope: overall architecture, `api-gateway`

## Context

EventFlow models the extraction of order fulfilment from a monolithic shop application in which
the catalogue, orders, payments and customer e-mails share one codebase and one database. The
typical pains of that starting point drive this design: every change needs a full regression cycle
and a coordinated release, and a slow payment provider can exhaust the shared connection pool and
take the whole shop down. A big-bang rewrite is ruled out: it freezes feature work for months and
moves all the risk to a single cut-over day. The monolith itself is not part of this repository.

## Decision

Extract capabilities one at a time behind a routing facade (the strangler fig):

1. Put `api-gateway` in front of the monolith first. Initially every path is forwarded to it
   (`eventflow.strangler.legacy-uri`, see `StranglerFallbackRouteConfig`).
2. For each capability, build the new service, then add a specific route for its paths. Routes
   are matched before the catch-all, so traffic moves with a configuration change and moves back
   just as easily.
3. Extract in order of pain and independence: notifications (no inbound dependencies), payments
   (isolates the slow provider), inventory, and finally orders, which owns the saga.
4. Until the order write path moves, the monolith publishes `OrderCreated` through an outbox of
   its own, so the new participants can go live against real traffic early.
5. When no path reaches the monolith any more, leave the property unset and the fallback route
   disappears; unknown paths then return 404.

## Consequences

* Each step is small, independently deployable and reversible.
* The gateway becomes a permanent part of the architecture, which also makes it the natural place
  for authentication, rate limiting and correlation ids.
* For a while two systems own parts of the same process; clear ownership per path (enforced by
  routing) and events as the only integration mechanism keep that manageable.
* The resulting services communicate asynchronously, which requires an explicit design for
  multi-step consistency - see [ADR 0002](0002-choreographed-saga-with-transactional-outbox.md).
