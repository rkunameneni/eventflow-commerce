package io.github.rkunameneni.eventflow.support.messaging;

import io.github.rkunameneni.eventflow.support.web.CorrelationId;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Restores the correlation id carried in a record's headers into the logging context, so log
 * lines written while handling an event - and any outbox events it produces - keep the id of the
 * request that started the saga.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> consumerRecord,
            Consumer<Object, Object> consumer) {
        Header header = consumerRecord.headers().lastHeader(CorrelationId.HEADER);
        String candidate = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        MDC.put(CorrelationId.MDC_KEY, CorrelationId.sanitizeOrGenerate(candidate));
        return consumerRecord;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> consumerRecord, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationId.MDC_KEY);
    }
}
