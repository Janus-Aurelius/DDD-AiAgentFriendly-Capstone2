package com.retail.pim.shared.exceptions;

/**
 * Base runtime exception thrown when a business invariant or domain rule is violated.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
