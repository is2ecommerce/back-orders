package com.example.backorders.exceptions;

public class OrderStateException extends RuntimeException {
    private final String currentState;
    private final String requiredState;

    public OrderStateException(String currentState, String requiredState) {
        super(String.format("La orden está en estado '%s'. Se requiere estado '%s'", currentState, requiredState));
        this.currentState = currentState;
        this.requiredState = requiredState;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getRequiredState() {
        return requiredState;
    }
}