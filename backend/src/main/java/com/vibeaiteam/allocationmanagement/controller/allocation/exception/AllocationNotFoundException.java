package com.vibeaiteam.allocationmanagement.controller.allocation.exception;

/**
 * 배정 대상 없음 예외(HTTP 404).
 */
public class AllocationNotFoundException extends RuntimeException {

  /**
   * 메시지를 담아 예외를 생성한다.
   */
  public AllocationNotFoundException(String message) {
    super(message);
  }
}
