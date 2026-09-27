package com.vibeaiteam.costrecording.controller.cost.exception;

public class CostForbiddenException extends RuntimeException {

    public CostForbiddenException(String message) {
        super(message);
    }
}