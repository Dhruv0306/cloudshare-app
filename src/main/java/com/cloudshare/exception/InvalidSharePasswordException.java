package com.cloudshare.exception;

public class InvalidSharePasswordException extends RuntimeException {
    public InvalidSharePasswordException(String message) {
        super(message);
    }
}
