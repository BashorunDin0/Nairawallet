package com.bashorundino.nairawallet.exception;

public class IdempotencyClaimConflictException extends RuntimeException{
    public IdempotencyClaimConflictException(String message, Throwable cause){
        super(message, cause);
    }
}
