package com.pharmacy.service;

/** A validation error shown to the user. The message is a key in messages*.properties. */
public class BusinessException extends RuntimeException {
    private final Object[] args;

    public BusinessException(String messageKey, Object... args) {
        super(messageKey);
        this.args = args;
    }

    public Object[] getArgs() { return args; }
}
