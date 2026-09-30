package com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto;

import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 증권사 응답 DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesFirmResponse {

  private Long id;
  private String name;
  private String externalCode;
  private String businessRegistrationNumber;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /**
   * 엔티티를 응답 DTO로 변환한다.
   */
  public static SecuritiesFirmResponse from(SecuritiesFirm securitiesFirm) {
    return SecuritiesFirmResponse.builder()
        .id(securitiesFirm.getId())
        .name(securitiesFirm.getName())
        .externalCode(securitiesFirm.getExternalCode())
        .businessRegistrationNumber(securitiesFirm.getBusinessRegistrationNumber())
        .status(securitiesFirm.getStatus().name())
        .createdAt(securitiesFirm.getCreatedAt())
        .updatedAt(securitiesFirm.getUpdatedAt())
        .build();
  }
}
