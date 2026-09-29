package io.github.rkunameneni.eventflow.events;

/** Raised when a message cannot be decoded into the event type expected on its topic. */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(Class<?> expectedType, Throwable cause) {
        super("Payload is not a valid " + expectedType.getSimpleName(), cause);
    }
}
