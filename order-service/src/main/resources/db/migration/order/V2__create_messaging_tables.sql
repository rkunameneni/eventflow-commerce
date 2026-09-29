-- Transactional outbox and idempotent-consumer tables used by the service-support starter.
create table outbox_events (
    position       bigint generated always as identity primary key,
    id             uuid         not null unique,
    aggregate_id   varchar(64)  not null,
    event_type     varchar(64)  not null,
    topic          varchar(128) not null,
    payload        jsonb        not null,
    correlation_id varchar(64),
    created_at     timestamptz  not null,
    published_at   timestamptz
);
create index outbox_events_pending_idx on outbox_events (position) where published_at is null;

create table processed_events (
    event_id     uuid        not null,
    consumer     varchar(64) not null,
    processed_at timestamptz not null,
    primary key (event_id, consumer)
);
