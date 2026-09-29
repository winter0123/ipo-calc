package com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritiesFirmUpdateRequest {

  private String name;

  private String externalCode;

  private String businessRegistrationNumber;
}
