package com.vibeaiteam.ipocatalog.controller.ipo.dto;

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
public class IpoImportFailure {

    private int rowNumber;

    private String reason;
}