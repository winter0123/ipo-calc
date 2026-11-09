package com.ipocalc.securitiesaccountmanagement.service.securitiesaccount;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountAccountNumberResponse;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.ipocalc.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountUpdateRequest;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountConflictException;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountNotFoundException;
import com.ipocalc.securitiesaccountmanagement.exception.SecuritiesAccountValidationException;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.ipocalc.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.ipocalc.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final SubscriptionExistenceCheck subscriptionExistenceCheck;
  private final AccountCredentialDeletion accountCredentialDeletion;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final SecuritiesAccountOwnershipVerifier ownershipVerifier;
  private final InvestorRepository investorRepository;
  private final Clock clock;
  private final EntityManager entityManager;

  private static final int ACCOUNT_NUMBER_MAX_LENGTH = 50;
  private static final int ACCOUNT_NAME_MAX_LENGTH = 255;

  private void validateAccountName(String accountName) {
    if (accountName != null && accountName.length() > ACCOUNT_NAME_MAX_LENGTH) {
      throw new SecuritiesAccountValidationException(
          "accountName은 " + ACCOUNT_NAME_MAX_LENGTH + "자 이하여야 합니다.");
    }
  }

  /**
   * 계좌번호 평문을 검증하고 정규화한다.
   *
   * @param plainAccountNumber 입력된 평문 계좌번호 (공백/하이픈 허용)
   * @return 공백/하이픈이 제거된 정규화된 숫자 문자열
   * @throws SecuritiesAccountValidationException 형식 오류 시
   */
  private String validateAndNormalizeAccountNumber(String plainAccountNumber) {
    String normalized = plainAccountNumber.replaceAll("[\\s-]", "");
    if (!normalized.matches("\\d{10,16}")) {
      throw new SecuritiesAccountValidationException("계좌번호는 10~16자리 숫자여야 합니다.");
    }
    if (normalized.length() > ACCOUNT_NUMBER_MAX_LENGTH) {
      throw new SecuritiesAccountValidationException(
          "accountNumber는 " + ACCOUNT_NUMBER_MAX_LENGTH + "자 이하여야 합니다.");
    }
    return normalized;
  }

  /** 증권계좌를 등록한다. */
  @Transactional
  public SecuritiesAccountResponse register(SecuritiesAccountRegisterRequest request) {
    Long investorId = request.getInvestorId();
    Long securitiesFirmId = request.getSecuritiesFirmId();

    if (investorId == null || securitiesFirmId == null) {
      throw new SecuritiesAccountValidationException("investorId, securitiesFirmId는 필수 입력값입니다.");
    }

    // 계좌명 길이 검증을 분기 전에 수행 (번호 유무와 관계없이 동일하게 적용)
    validateAccountName(request.getAccountName());

    ownershipVerifier.requireOwnedInvestor(investorId);

    securitiesFirmRepository
        .findByIdAndDeletedAtIsNull(securitiesFirmId)
        .orElseThrow(() -> new SecuritiesAccountNotFoundException("존재하지 않는 증권사입니다"));

    LocalDateTime now = LocalDateTime.now(clock);

    // 투자자 행 락으로 동시성 제어 (계좌번호 유무와 관계없이 먼저 락 획득)
    investorRepository
        .findByIdForUpdate(investorId)
        .orElseThrow(() -> new SecuritiesAccountNotFoundException("투자자를 찾을 수 없습니다."));

    // 계좌번호가 제공되지 않았거나 공백인 경우: 기존 활성 계좌 재사용 또는 신규 생성(null)
    String accountNumber = request.getAccountNumber();
    if (accountNumber == null || accountNumber.isBlank()) {
      // 같은 투자자+증권사의 가장 오래된 활성 계좌 조회
      var existingAccount = findOldestActiveAccount(investorId, securitiesFirmId);

      if (existingAccount.isPresent()) {
        // 기존 활성 계좌 재사용 (updatedAt 변경 없음)
        SecuritiesAccount reused = existingAccount.get();
        ownershipVerifier.requireOwner(reused);
        return toMaskedResponse(reused);
      }

      // 기존 활성 계좌 없음: accountNumber=null로 신규 생성
      SecuritiesAccount entity =
          SecuritiesAccount.builder()
              .investorId(investorId)
              .securitiesFirmId(securitiesFirmId)
              .accountNumber(null)
              .accountName(request.getAccountName())
              .status(RecordStatus.ACTIVE)
              .deletedAt(null)
              .createdAt(now)
              .updatedAt(now)
              .build();

      SecuritiesAccount saved = securitiesAccountRepository.save(entity);
      return SecuritiesAccountResponse.from(saved, null);
    }

    // 계좌번호가 제공된 경우: 기존 검증·암호화 후 신규 생성
    String plainAccountNumber = validateAndNormalizeAccountNumber(accountNumber);

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

    String encrypted = account.getAccountNumber();
    if (encrypted == null || encrypted.isBlank()) {
      throw new SecuritiesAccountValidationException("계좌번호가 등록되지 않았습니다. 계좌 수정에서 계좌번호를 입력해 주세요.");
    }

    String plainAccountNumber = accountNumberEncryptor.decrypt(encrypted);
    Instant exposedAt = Instant.now(clock);

    // D-SA-005: 감사 로그는 SLF4J 애플리케이션 로그로만 기록 (별도 DB 테이블 없음)
    // 평문 조회 성공 시에만 기록 (번호 없을 때는 기록하지 않음)
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
    // 투자자 행 락을 먼저 획득한 뒤 최신 엔티티를 읽어 stale read 방지
    SecuritiesAccount account = findActive(id);
    Long investorId = account.getInvestorId();

    // 락 획득
    investorRepository
        .findByIdForUpdate(investorId)
        .orElseThrow(() -> new SecuritiesAccountNotFoundException("투자자를 찾을 수 없습니다."));

    // 락 획득 후 엔티티 재조회로 최신 상태 확보 (1차 캐시 stale data 방지)
    entityManager.refresh(account);
    if (account.getDeletedAt() != null) {
      throw new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다.");
    }

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

    // accountNumber 처리: 미전달=무변경, 공백=400, 기존 암호문 존재=409, 유효값=검증+암호화 저장
    String accountNumber = request.getAccountNumber();
    if (accountNumber != null) {
      if (accountNumber.isBlank()) {
        throw new SecuritiesAccountValidationException("계좌번호는 10~16자리 숫자여야 합니다.");
      }
      if (account.getAccountNumber() != null && !account.getAccountNumber().isBlank()) {
        throw new SecuritiesAccountConflictException("이미 계좌번호가 등록되어 있습니다");
      }
      String plainAccountNumber = validateAndNormalizeAccountNumber(accountNumber);
      account.setAccountNumber(
          accountNumberEncryptor.encrypt(plainAccountNumber), LocalDateTime.now(clock));
    }

    account.changeDetails(
        request.getSecuritiesFirmId(), request.getAccountName(), LocalDateTime.now(clock));
    SecuritiesAccount saved = securitiesAccountRepository.save(account);

    return toMaskedResponse(saved);
  }

  /** 증권계좌를 비활성화(소프트 삭제)한다. */
  @Transactional
  public void deactivate(Long id) {
    // 투자자 행 락을 먼저 획득한 뒤 최신 엔티티를 읽어 stale read 방지
    SecuritiesAccount account = findActive(id);
    Long investorId = account.getInvestorId();

    // 락 획득
    investorRepository
        .findByIdForUpdate(investorId)
        .orElseThrow(() -> new SecuritiesAccountNotFoundException("투자자를 찾을 수 없습니다."));

    // 락 획득 후 엔티티 재조회로 최신 상태 확보 (1차 캐시 stale data 방지)
    entityManager.refresh(account);
    if (account.getDeletedAt() != null) {
      throw new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다.");
    }

    ownershipVerifier.requireOwner(account);

    // 청약 이력 존재 여부만 체크 (취소/논리삭제 포함 모든 이력)
    if (subscriptionExistenceCheck.existsBySecuritiesAccountId(id)) {
      throw new SecuritiesAccountConflictException("연결된 청약 이력이 있어 비활성화할 수 없습니다");
    }

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

  /** 투자자+증권사의 가장 오래된 활성 계좌를 조회한다. */
  @SuppressWarnings("checkstyle:LineLength")
  private Optional<SecuritiesAccount> findOldestActiveAccount(
      Long investorId, Long securitiesFirmId) {
    return securitiesAccountRepository
        .findFirstByInvestorIdAndSecuritiesFirmIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
            investorId, securitiesFirmId, RecordStatus.ACTIVE);
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
    String encrypted = account.getAccountNumber();
    String maskedAccountNumber;
    if (encrypted == null || encrypted.isBlank()) {
      maskedAccountNumber = null;
    } else {
      String plainAccountNumber = accountNumberEncryptor.decrypt(encrypted);
      maskedAccountNumber = AccountNumberMasker.mask(plainAccountNumber);
    }
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
