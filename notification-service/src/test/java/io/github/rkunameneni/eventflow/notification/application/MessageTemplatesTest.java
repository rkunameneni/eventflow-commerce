package io.github.rkunameneni.eventflow.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkunameneni.eventflow.events.OrderCancelled;
import io.github.rkunameneni.eventflow.events.OrderConfirmed;
import io.github.rkunameneni.eventflow.notification.domain.Message;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessageTemplatesTest {

    private static final UUID ORDER_ID = UUID.fromString("3f2a9c4e-1111-2222-3333-444455556666");

    private final MessageTemplates templates = new MessageTemplates();

    @Test
    void confirmationMentionsTheAmountCharged() {
        Message message = templates.confirmed(new OrderConfirmed(UUID.randomUUID(), ORDER_ID, "alice",
                new BigDecimal("183.50"), "USD", Instant.now()));

        assertThat(message.subject()).isEqualTo("Your order 3F2A9C4E is confirmed");
        assertThat(message.body()).contains("183.50 USD");
    }

    @Test
    void cancellationNeverLeaksInternalReasonCodes() {
        Message message = templates.cancelled(new OrderCancelled(UUID.randomUUID(), ORDER_ID, "alice",
                "PRICE_CHANGED: SKU-1 now costs 12.00 USD", Instant.now()));

        assertThat(message.body())
                .contains("the price of an item changed")
                .doesNotContain("SKU-1")
                .contains("You have not been charged.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "INSUFFICIENT_STOCK: SKU-9 | one of the items is out of stock.",
        "PAYMENT_DECLINED: insufficient_funds | your payment was declined.",
        "UNKNOWN_SKU: SKU-0 | one of the items is no longer available.",
        "SOMETHING_NEW | an unexpected problem occurred."
    })
    void translatesReasonCodes(String code, String explanation) {
        assertThat(MessageTemplates.explain(code)).isEqualTo(explanation);
    }
}
