package com.vibeaiteam.salemanagement.controller.sale.exception;

public class SaleAllocationExceededException extends RuntimeException {

  public SaleAllocationExceededException(String message) {
    super(message);
  }
}
