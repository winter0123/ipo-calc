package com.ipocalc.accountauthentication.controller.dto;

/**
 * 현재 로그인한 계정 정보. 비밀번호 해시 등 민감 정보는 담지 않는다.
 *
 * @param accountId 계정 ID
 * @param email 이메일
 * @param name 이름
 * @param nickname 닉네임
 * @param role 역할(USER, ADMIN)
 */
public record CurrentAccountResponse(
    Long accountId, String email, String name, String nickname, String role) {}
