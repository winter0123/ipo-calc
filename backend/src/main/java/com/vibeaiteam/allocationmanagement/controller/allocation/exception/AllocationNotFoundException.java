package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

public class AllocationNotFoundException extends RuntimeException {

    public AllocationNotFoundException(String message) {
        super(message);
    }
}
