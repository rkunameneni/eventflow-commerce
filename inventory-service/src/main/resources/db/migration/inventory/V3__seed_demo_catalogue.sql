-- Demo catalogue for local runs. SKU-LAPTOP is priced above the mock payment provider's
-- approval limit, which makes the "payment declined" saga path easy to try by hand.
insert into products (sku, name, unit_price, currency, available_quantity, reserved_quantity, updated_at, version)
values ('SKU-KEYBOARD', 'Mechanical keyboard', 79.00, 'USD', 100, 0, now(), 0),
       ('SKU-MOUSE', 'Wireless mouse', 25.50, 'USD', 250, 0, now(), 0),
       ('SKU-MONITOR', '27-inch monitor', 249.00, 'USD', 40, 0, now(), 0),
       ('SKU-HEADSET', 'Noise-cancelling headset', 59.99, 'USD', 3, 0, now(), 0),
       ('SKU-LAPTOP', '14-inch laptop', 1499.00, 'USD', 10, 0, now(), 0);
