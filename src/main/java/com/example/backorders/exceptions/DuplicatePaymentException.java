package com.example.backorders.exceptions;

public class DuplicatePaymentException extends PaymentException {
    public DuplicatePaymentException(String message) {
        super(message);
    }
}
