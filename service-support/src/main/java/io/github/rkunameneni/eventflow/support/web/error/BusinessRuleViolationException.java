package io.github.rkunameneni.eventflow.support.web.error;

/**
 * A syntactically valid request that breaks a business rule. Mapped to HTTP 422 with a stable,
 * machine-readable {@code code} so clients can branch on it without parsing messages.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String code;

    public BusinessRuleViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
