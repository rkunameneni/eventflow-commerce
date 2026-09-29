package io.github.rkunameneni.eventflow.support.web.error;

/** The requested resource does not exist, or the caller is not allowed to know that it exists. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found");
    }
}
