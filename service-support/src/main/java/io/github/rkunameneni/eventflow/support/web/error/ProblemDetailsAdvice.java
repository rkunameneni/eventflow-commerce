package io.github.rkunameneni.eventflow.support.web.error;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every error as an RFC 7807 {@code application/problem+json} document. Spring MVC's own
 * exceptions (bad JSON, missing headers, method validation, ...) are handled by the superclass;
 * this class adds the domain exceptions and enriches validation errors with per-field messages.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final String TYPE_PREFIX = "urn:eventflow:problem:";

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRule(BusinessRuleViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule",
                "Business rule violated", ex.getMessage());
        problem.setProperty("code", ex.code());
        return problem;
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConcurrentModification(RuntimeException ex) {
        logger.info("Rejected conflicting write: " + ex.getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "Conflicting update",
                "The resource was modified concurrently; retry the request.");
    }

    /** Raised by Spring Data when a client sorts by an unknown property, e.g. {@code ?sort=password}. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-sort", "Invalid sort property",
                "Cannot sort by '" + ex.getPropertyName() + "'.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return validationProblem(errors);
    }

    /**
     * Raised instead of {@link MethodArgumentNotValidException} when a controller method also has
     * constraints on plain parameters (e.g. a validated header), so both paths render the same.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getAllValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors()
                        .forEach(error -> errors.putIfAbsent(name, error.getDefaultMessage()));
            }
        }
        return validationProblem(errors);
    }

    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        return parameter.getParameterName();
    }

    private static ResponseEntity<Object> validationProblem(Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "The request contains invalid fields.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        return problem;
    }
}
