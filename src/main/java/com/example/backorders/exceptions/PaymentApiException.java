package com.example.backorders.exceptions;

public class PaymentApiException extends PaymentException {
    public PaymentApiException(String message) {
        super(message);
    }

    public PaymentApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
