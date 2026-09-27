package com.vibeaiteam.costrecording.controller.cost.exception;

public class CostNotFoundException extends RuntimeException {

    public CostNotFoundException(String message) {
        super(message);
    }
}