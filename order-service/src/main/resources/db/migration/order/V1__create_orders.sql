create table orders (
    id            uuid primary key,
    customer_id   varchar(64)    not null,
    status        varchar(32)    not null,
    status_reason varchar(255),
    total_amount  numeric(12, 2) not null check (total_amount > 0),
    currency      varchar(3)     not null,
    created_at    timestamptz    not null,
    updated_at    timestamptz    not null,
    version       bigint         not null
);
create index orders_customer_created_idx on orders (customer_id, created_at desc);
create index orders_status_idx on orders (status);

create table order_items (
    id          uuid primary key,
    order_id    uuid           not null references orders (id) on delete cascade,
    line_number int            not null,
    sku         varchar(64)    not null,
    quantity    int            not null check (quantity > 0),
    unit_price  numeric(12, 2) not null check (unit_price > 0),
    unique (order_id, line_number)
);

create table idempotency_keys (
    customer_id     varchar(64) not null,
    idempotency_key varchar(64) not null,
    request_hash    varchar(64) not null,
    order_id        uuid        not null references orders (id),
    created_at      timestamptz not null,
    primary key (customer_id, idempotency_key)
);
