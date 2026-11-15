package com.retail.pim.shared.exceptions;

/**
 * Exception thrown when an expected domain entity or aggregate root cannot be found.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(String.format("%s with identifier '%s' was not found", resourceName, id));
    }
}
