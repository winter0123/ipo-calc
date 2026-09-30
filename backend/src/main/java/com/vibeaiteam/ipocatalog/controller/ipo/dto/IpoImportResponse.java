package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공모주 응답 DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoImportResponse {

  private int successCount;

  private int failureCount;

  private List<IpoImportFailure> failures;
}
