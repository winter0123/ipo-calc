package com.vibeaiteam.accountauthentication.controller.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통 에러 응답 DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

  private String error;
  private String message;
  private List<FieldError> fieldErrors;

  /**
   * 필드별 검증 오류 항목.
   */
  @Getter
  @Setter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldError {
    private String field;
    private String message;
  }
}
