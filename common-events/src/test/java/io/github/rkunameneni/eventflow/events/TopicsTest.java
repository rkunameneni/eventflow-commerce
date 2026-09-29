package io.github.rkunameneni.eventflow.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopicsTest {

    @Test
    void mapsEventsToTheirTopic() {
        var event = new PaymentFailed(UUID.randomUUID(), UUID.randomUUID(), "c-1", "DECLINED", Instant.now());

        assertThat(Topics.topicFor(event)).isEqualTo(Topics.PAYMENT_FAILED);
    }

    @Test
    void everyPermittedEventTypeIsMappedToADistinctTopic() {
        assertThat(SagaEvent.class.getPermittedSubclasses()).hasSize(Topics.ALL.size());
        assertThat(Topics.ALL).doesNotHaveDuplicates();
        var confirmed = new OrderConfirmed(UUID.randomUUID(), UUID.randomUUID(), "c-1",
                BigDecimal.TEN, "USD", Instant.now());
        assertThat(Topics.topicFor(confirmed)).isEqualTo(Topics.ORDER_CONFIRMED);
    }

    @Test
    void deadLetterTopicUsesTheDltSuffix() {
        assertThat(Topics.deadLetterTopic(Topics.ORDER_CREATED)).isEqualTo("eventflow.order.created.v1.DLT");
    }
}
