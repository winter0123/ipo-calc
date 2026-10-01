package com.vibeaiteam.accountauthentication.controller.dto;

import java.time.LocalDateTime;

/**
 * 관리자 화면용 계정 정보. 비밀번호 해시 등 민감 정보는 담지 않는다.
 *
 * @param id 계정 ID
 * @param email 이메일
 * @param name 이름
 * @param role 역할(USER, ADMIN)
 * @param approved 승인 여부
 * @param createdAt 가입 시각
 */
public record AdminAccountResponse(
    Long id, String email, String name, String role, boolean approved, LocalDateTime createdAt) {}
