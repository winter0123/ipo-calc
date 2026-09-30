package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CSV 가져오기에서 실패한 행의 번호와 사유.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpoImportFailure {

  private int rowNumber;

  private String reason;
}
