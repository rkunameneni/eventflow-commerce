create table products (
    sku                varchar(64) primary key,
    name               varchar(200)   not null,
    unit_price         numeric(12, 2) not null check (unit_price > 0),
    currency           varchar(3)     not null,
    available_quantity int            not null check (available_quantity >= 0),
    reserved_quantity  int            not null default 0 check (reserved_quantity >= 0),
    updated_at         timestamptz    not null,
    version            bigint         not null
);

create table reservations (
    id         uuid primary key,
    order_id   uuid        not null,
    sku        varchar(64) not null references products (sku),
    quantity   int         not null check (quantity > 0),
    status     varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index reservations_order_idx on reservations (order_id, status);
