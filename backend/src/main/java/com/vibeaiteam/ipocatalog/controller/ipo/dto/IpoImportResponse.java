package com.vibeaiteam.ipocatalog.controller.ipo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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