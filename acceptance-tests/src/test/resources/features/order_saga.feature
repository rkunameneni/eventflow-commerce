Feature: Order fulfilment saga
  An order is confirmed only when its stock is reserved and the customer has been charged.
  When any step fails the order is cancelled, and whatever was already done is compensated.

  Background:
    Given the payment provider approves charges up to 500.00

  Scenario: A paid order is confirmed and its stock is committed
    Given the catalogue has product "SKU-BDD-DESK" priced 40.00 USD with 10 units in stock
    When customer "alice" orders 3 of "SKU-BDD-DESK"
    Then the order is eventually "CONFIRMED"
    And the payment for the order is "COMPLETED"
    And product "SKU-BDD-DESK" has 7 units available and 0 reserved
    And customer "alice" is notified that the order was confirmed

  Scenario: A declined payment cancels the order and releases the reserved stock
    Given the catalogue has product "SKU-BDD-CHAIR" priced 300.00 USD with 5 units in stock
    When customer "bob" orders 2 of "SKU-BDD-CHAIR"
    Then the order is eventually "CANCELLED" because "PAYMENT_DECLINED: amount_over_limit"
    And the payment for the order is "FAILED"
    And product "SKU-BDD-CHAIR" has 5 units available and 0 reserved
    And customer "bob" is notified that the order was cancelled

  Scenario: An order for more than is in stock is cancelled before any charge
    Given the catalogue has product "SKU-BDD-LAMP" priced 10.00 USD with 1 units in stock
    When customer "carol" orders 2 of "SKU-BDD-LAMP"
    Then the order is eventually "CANCELLED" because "INSUFFICIENT_STOCK: SKU-BDD-LAMP"
    And no payment was attempted for the order
    And customer "carol" is notified that the order was cancelled
