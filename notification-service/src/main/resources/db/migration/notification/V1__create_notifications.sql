create table notifications (
    id          uuid primary key,
    order_id    uuid          not null,
    customer_id varchar(64)   not null,
    type        varchar(32)   not null,
    channel     varchar(16)   not null,
    subject     varchar(255)  not null,
    body        varchar(2000) not null,
    sent_at     timestamptz   not null
);
create index notifications_customer_sent_idx on notifications (customer_id, sent_at desc);
