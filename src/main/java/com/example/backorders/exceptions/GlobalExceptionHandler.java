package com.example.backorders.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderStateException.class)
    public ResponseEntity<?> handleOrderStateException(OrderStateException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(Map.of(
                "error", "Error de estado de orden",
                "message", ex.getMessage(),
                "currentState", ex.getCurrentState(),
                "requiredState", ex.getRequiredState()
            ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.example.backorders.exceptions.InsufficientFundsException.class)
    public ResponseEntity<?> handleInsufficient(InsufficientFundsException ex) {
        return ResponseEntity
            .status(HttpStatus.PAYMENT_REQUIRED)
            .body(Map.of("error", "Pago rechazado", "message", ex.getMessage()));
    }

    @ExceptionHandler(com.example.backorders.exceptions.DuplicatePaymentException.class)
    public ResponseEntity<?> handleDuplicate(DuplicatePaymentException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of("error", "Pago duplicado", "message", ex.getMessage()));
    }

    @ExceptionHandler(com.example.backorders.exceptions.PaymentApiException.class)
    public ResponseEntity<?> handlePaymentApi(PaymentApiException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "Error en pasarela de pagos", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAny(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Error interno del servidor"));
    }
}
