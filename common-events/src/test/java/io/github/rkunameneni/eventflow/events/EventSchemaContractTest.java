package io.github.rkunameneni.eventflow.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Guards against the published JSON Schemas drifting away from the Java records. Non-Java
 * consumers code against the schemas, so a renamed or added field must be reflected there.
 */
class EventSchemaContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Class<?>> eventTypes() {
        return Arrays.stream(SagaEvent.class.getPermittedSubclasses());
    }

    @ParameterizedTest
    @MethodSource("eventTypes")
    void schemaDeclaresEveryRecordComponentAsRequired(Class<?> eventType) throws IOException {
        JsonNode schema = loadSchema(eventType);

        Set<String> required = new HashSet<>();
        schema.get("required").forEach(node -> required.add(node.asText()));
        Set<String> properties = new HashSet<>();
        schema.get("properties").fieldNames().forEachRemaining(properties::add);

        Set<String> components = new HashSet<>();
        for (RecordComponent component : eventType.getRecordComponents()) {
            components.add(component.getName());
        }

        assertThat(required).isEqualTo(components);
        assertThat(properties).isEqualTo(components);
        assertThat(schema.get("title").asText()).isEqualTo(eventType.getSimpleName());
    }

    @ParameterizedTest
    @MethodSource("eventTypes")
    void everyEventTypeHasItsOwnTopic(Class<?> eventType) throws IOException {
        JsonNode schema = loadSchema(eventType);
        String description = schema.get("description").asText();

        assertThat(Topics.ALL).anySatisfy(topic -> assertThat(description).contains(topic));
    }

    private static JsonNode loadSchema(Class<?> eventType) throws IOException {
        String path = "/schemas/" + eventType.getSimpleName() + ".schema.json";
        try (InputStream in = EventSchemaContractTest.class.getResourceAsStream(path)) {
            assertThat(in).as("schema %s", path).isNotNull();
            return MAPPER.readTree(in);
        }
    }
}
