package com.ipocalc.securitiesaccountmanagement.service.securitiesaccount;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountAccountNumberResponse;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountUpdateRequest;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountNotFoundException;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountValidationException;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 증권계좌 관리 서비스. 계좌번호는 암호화해 저장한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecuritiesAccountService {
  private static final String DEFAULT_STATUS = RecordStatus.ACTIVE.name();
  private static final Set<String> SUPPORTED_STATUS =
      Set.of(RecordStatus.ACTIVE.name(), RecordStatus.INACTIVE.name(), "BOTH");

  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final AccountCredentialDeletion accountCredentialDeletion;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final SecuritiesAccountOwnershipVerifier ownershipVerifier;
  private final Clock clock;

  private static final int ACCOUNT_NUMBER_MAX_LENGTH = 50;
  private static final int ACCOUNT_NAME_MAX_LENGTH = 255;

  private void validateAccountName(String accountName) {
    if (accountName != null && accountName.length() > ACCOUNT_NAME_MAX_LENGTH) {
      throw new SecuritiesAccountValidationException(
          "accountName은 " + ACCOUNT_NAME_MAX_LENGTH + "자 이하여야 합니다.");
    }
  }

  /** 증권계좌를 등록한다. */
  @Transactional
  public SecuritiesAccountResponse register(SecuritiesAccountRegisterRequest request) {
    Long investorId = request.getInvestorId();
    Long securitiesFirmId = request.getSecuritiesFirmId();
    if (investorId == null || securitiesFirmId == null || request.getAccountNumber() == null) {
      throw new SecuritiesAccountValidationException(
          "investorId, securitiesFirmId, accountNumber는 필수 입력값입니다.");
    }
    // 공백·하이픈을 제거한 숫자 10~16자리만 허용하고 정규화한 값을 저장한다(화면 규칙과 동일).
    String plainAccountNumber = request.getAccountNumber().replaceAll("[\\s-]", "");
    if (!plainAccountNumber.matches("\\d{10,16}")) {
      throw new SecuritiesAccountValidationException("계좌번호는 10~16자리 숫자여야 합니다.");
    }

    validateAccountName(request.getAccountName());
    // 암호문이 컬럼(255자)을 넘지 않도록 원문 길이를 제한한다.
    if (plainAccountNumber.length() > ACCOUNT_NUMBER_MAX_LENGTH) {
      throw new SecuritiesAccountValidationException(
          "accountNumber는 " + ACCOUNT_NUMBER_MAX_LENGTH + "자 이하여야 합니다.");
    }

    ownershipVerifier.requireOwnedInvestor(investorId);

    securitiesFirmRepository
        .findByIdAndDeletedAtIsNull(securitiesFirmId)
        .orElseThrow(() -> new SecuritiesAccountNotFoundException("존재하지 않는 증권사입니다"));

    LocalDateTime now = LocalDateTime.now(clock);
    SecuritiesAccount entity =
        SecuritiesAccount.builder()
            .investorId(investorId)
            .securitiesFirmId(securitiesFirmId)
            .accountNumber(accountNumberEncryptor.encrypt(plainAccountNumber))
            .accountName(request.getAccountName())
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    SecuritiesAccount saved = securitiesAccountRepository.save(entity);
    // 저장 직후이므로 저장에 사용한 원본 평문을 그대로 마스킹에 사용한다 (다시 복호화할 필요 없음)
    return SecuritiesAccountResponse.from(saved, AccountNumberMasker.mask(plainAccountNumber));
  }

  /** 증권계좌 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<SecuritiesAccountResponse> list(Long investorId, String status) {
    if (investorId == null) {
      throw new SecuritiesAccountValidationException("investorId는 필수 입력값입니다.");
    }
    String normalized = normalizeStatus(status);

    ownershipVerifier.requireOwnedInvestor(investorId);

    List<SecuritiesAccount> accounts =
        "BOTH".equals(normalized)
            ? securitiesAccountRepository.findByInvestorId(investorId)
            : securitiesAccountRepository.findByInvestorIdAndStatus(
                investorId, RecordStatus.valueOf(normalized));

    // 증권사 이름은 한 번에 읽어 계좌 수와 무관하게 쿼리 수를 일정하게 유지한다.
    Map<Long, String> firmNames =
        securitiesFirmRepository
            .findAllById(
                accounts.stream().map(SecuritiesAccount::getSecuritiesFirmId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(SecuritiesFirm::getId, SecuritiesFirm::getName));
    return accounts.stream()
        .map(a -> toMaskedResponse(a, firmNames.get(a.getSecuritiesFirmId())))
        .toList();
  }

  /** ID로 증권계좌를 조회한다. */
  @Transactional(readOnly = true)
  public SecuritiesAccountResponse getById(Long id) {
    SecuritiesAccount account = findActive(id);
    ownershipVerifier.requireOwner(account);

    return toMaskedResponse(account);
  }

  /** 복호화한 계좌번호 전체를 반환한다. 소유권을 검증한다. */
  @Transactional(readOnly = true)
  public SecuritiesAccountAccountNumberResponse getAccountNumber(Long id) {
    SecuritiesAccount account = findActive(id);
    Long currentAccountId = ownershipVerifier.requireOwner(account);

    String plainAccountNumber = accountNumberEncryptor.decrypt(account.getAccountNumber());
    Instant exposedAt = Instant.now(clock);

    // D-SA-005: 감사 로그는 SLF4J 애플리케이션 로그로만 기록 (별도 DB 테이블 없음)
    log.info(
        "계좌번호 노출: accountId={}, requestedByAccountId={}, exposedAt={}",
        id,
        currentAccountId,
        exposedAt);

    return SecuritiesAccountAccountNumberResponse.of(id, plainAccountNumber, exposedAt);
  }

  /** 증권계좌 정보를 수정한다. */
  @Transactional
  public SecuritiesAccountResponse update(Long id, SecuritiesAccountUpdateRequest request) {
    SecuritiesAccount account = findActive(id);
    ownershipVerifier.requireOwner(account);

    if (request.getSecuritiesFirmId() != null) {
      SecuritiesFirm firm =
          securitiesFirmRepository
              .findById(request.getSecuritiesFirmId())
              .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 증권사입니다"));
      if (firm.getStatus() != RecordStatus.ACTIVE) {
        throw new SecuritiesAccountValidationException("비활성화된 증권사로는 변경할 수 없습니다.");
      }
    }

    validateAccountName(request.getAccountName());
    account.changeDetails(
        request.getSecuritiesFirmId(), request.getAccountName(), LocalDateTime.now(clock));
    SecuritiesAccount saved = securitiesAccountRepository.save(account);

    return toMaskedResponse(saved);
  }

  /** 증권계좌를 비활성화(소프트 삭제)한다. */
  @Transactional
  public void deactivate(Long id) {
    SecuritiesAccount account = findActive(id);
    ownershipVerifier.requireOwner(account);

    LocalDateTime now = LocalDateTime.now(clock);
    account.deactivate(now);
    securitiesAccountRepository.saveAndFlush(account);
    // 비밀키 암호문을 남기지 않도록 API 인증정보는 물리 삭제한다.
    accountCredentialDeletion.deleteBySecuritiesAccountId(id);
  }

  private SecuritiesAccount findActive(Long id) {
    return securitiesAccountRepository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));
  }

  private SecuritiesAccountResponse toMaskedResponse(SecuritiesAccount account) {
    // 표시용 조회이므로 findById(deletedAt 필터 없음)를 쓴다 - 증권사가 비활성화돼도
    // 기존에 연결된 계좌는 그대로 유지되므로(architecture.md), 이름도 계속 보여야 한다.
    String securitiesFirmName =
        securitiesFirmRepository
            .findById(account.getSecuritiesFirmId())
            .map(SecuritiesFirm::getName)
            .orElse(null);
    return toMaskedResponse(account, securitiesFirmName);
  }

  private SecuritiesAccountResponse toMaskedResponse(
      SecuritiesAccount account, String securitiesFirmName) {
    String plainAccountNumber = accountNumberEncryptor.decrypt(account.getAccountNumber());
    String maskedAccountNumber = AccountNumberMasker.mask(plainAccountNumber);
    return SecuritiesAccountResponse.from(account, maskedAccountNumber, securitiesFirmName);
  }

  private String normalizeStatus(String status) {
    if (status == null || status.trim().isEmpty()) {
      return DEFAULT_STATUS;
    }
    String upper = status.trim().toUpperCase();
    if (!SUPPORTED_STATUS.contains(upper)) {
      throw new SecuritiesAccountValidationException("status는 ACTIVE, INACTIVE, BOTH 중 하나여야 합니다.");
    }
    return upper;
  }
}
