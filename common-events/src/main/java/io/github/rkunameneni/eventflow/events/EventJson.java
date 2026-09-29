package io.github.rkunameneni.eventflow.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON codec for saga events.
 *
 * <p>Unknown properties are ignored on read so producers can add optional fields without
 * breaking consumers that have not been redeployed yet (forward compatibility).
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EventJson() {
    }

    public static String write(SagaEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event.getClass().getSimpleName(), e);
        }
    }

    /**
     * Decodes a payload.
     *
     * @throws MalformedEventException if the payload is not valid JSON for {@code type}. Such a
     *         message can never succeed, so consumers treat it as non-retryable.
     */
    public static <T extends SagaEvent> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new MalformedEventException(type, e);
        }
    }
}
