package com.vibeaiteam.brokerintegration.controller.credential;

import com.vibeaiteam.brokerintegration.controller.credential.dto.ApiCredentialRegisterRequest;
import com.vibeaiteam.brokerintegration.controller.credential.dto.ApiCredentialReplaceRequest;
import com.vibeaiteam.brokerintegration.controller.credential.dto.ApiCredentialResponse;
import com.vibeaiteam.brokerintegration.service.credential.ApiCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 증권계좌별 브로커 API 인증정보의 등록/조회/연결해제 API를 제공한다.
 *
 * <p>모든 요청은 인증이 필요하며, 대상 계좌의 소유권은 서비스 계층의 2단계 검증(SecuritiesAccount → Investor → Account)에서 확인한다.
 * 응답에는 연결 상태·마스킹된 계좌번호·마지막 동기화 시각만 담는다.
 */
@RestController
@RequestMapping("/securities-accounts/{securitiesAccountId}/api-credential")
@RequiredArgsConstructor
public class ApiCredentialController {

  private final ApiCredentialService apiCredentialService;

  /**
   * 브로커 API 인증정보를 등록하고 201 Created와 함께 연결 상태를 반환한다. 타 계정 계좌는 403, 같은 provider 중복 등록은 409, 브로커 암호화 키
   * 미설정은 503을 반환한다.
   */
  @PostMapping
  public ResponseEntity<ApiCredentialResponse> register(
      @PathVariable Long securitiesAccountId,
      @Valid @RequestBody ApiCredentialRegisterRequest request) {
    ApiCredentialResponse response =
        apiCredentialService.register(
            securitiesAccountId,
            request.getApiProvider(),
            request.getAppKey(),
            request.getAppSecret(),
            request.getKeyIssuedOn());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /** 연결된 증권사 API 키를 새 키로 교체한다. 연결이 없으면 404, 타 계정 계좌는 403, 브로커 암호화 키 미설정은 503이다. */
  @PutMapping
  public ResponseEntity<ApiCredentialResponse> replace(
      @PathVariable Long securitiesAccountId,
      @Valid @RequestBody ApiCredentialReplaceRequest request) {
    return ResponseEntity.ok(
        apiCredentialService.replace(
            securitiesAccountId,
            request.getAppKey(),
            request.getAppSecret(),
            request.getKeyIssuedOn()));
  }

  /** 증권계좌의 브로커 API 인증정보 연결 상태를 조회한다. 연결 전이면 DISCONNECTED, 존재하지 않는 계좌는 404, 타 계정 계좌는 403을 반환한다. */
  @GetMapping
  public ResponseEntity<ApiCredentialResponse> getStatus(@PathVariable Long securitiesAccountId) {
    return ResponseEntity.ok(apiCredentialService.getStatus(securitiesAccountId));
  }

  /**
   * 증권계좌의 브로커 API 인증정보를 연결 해제(소프트 삭제)하고 204 No Content를 반환한다. 존재하지 않는 계좌는 404, 타 계정 계좌는 403을 반환한다.
   */
  @DeleteMapping
  public ResponseEntity<Void> disconnect(@PathVariable Long securitiesAccountId) {
    apiCredentialService.disconnect(securitiesAccountId);
    return ResponseEntity.noContent().build();
  }
}
