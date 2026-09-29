package io.github.rkunameneni.eventflow.events;

import java.math.BigDecimal;

/** A single line of an order, priced as quoted to the customer when the order was placed. */
public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
