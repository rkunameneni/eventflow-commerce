create table payments (
    id                 uuid primary key,
    order_id           uuid           not null unique,
    customer_id        varchar(64)    not null,
    amount             numeric(12, 2) not null check (amount > 0),
    currency           varchar(3)     not null,
    status             varchar(16)    not null,
    provider           varchar(32)    not null,
    provider_reference varchar(128),
    failure_reason     varchar(255),
    created_at         timestamptz    not null,
    updated_at         timestamptz    not null,
    version            bigint         not null
);
create index payments_customer_idx on payments (customer_id);
