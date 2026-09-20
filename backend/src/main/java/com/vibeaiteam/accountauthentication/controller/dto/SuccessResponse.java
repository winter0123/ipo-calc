package com.vibeaiteam.accountauthentication.controller.dto;

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
public class SuccessResponse {

    private String message;

    public static SuccessResponse of(String message) {
        return SuccessResponse.builder().message(message).build();
    }
}