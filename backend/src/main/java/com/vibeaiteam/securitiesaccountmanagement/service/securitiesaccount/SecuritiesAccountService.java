package com.vibeaiteam.securitiesaccountmanagement.service.securitiesaccount;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountAccountNumberResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountUpdateRequest;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountConflictException;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountForbiddenException;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountNotFoundException;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountValidationException;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증권계좌 관리 서비스. 계좌번호는 암호화해 저장한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecuritiesAccountService {
  private static final String DEFAULT_STATUS = RecordStatus.ACTIVE.name();
  private static final Set<String> SUPPORTED_STATUS =
      Set.of(RecordStatus.ACTIVE.name(), RecordStatus.INACTIVE.name(), "BOTH");

  private final InvestorRepository investorRepository;
  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final AccountNumberEncryptor accountNumberEncryptor;
  private final OwnershipGuard ownershipGuard;
  private final Clock clock;

  /**
   * 증권계좌를 등록한다.
   */
  @Transactional
  public SecuritiesAccountResponse register(SecuritiesAccountRegisterRequest request) {
    Long investorId = request.getInvestorId();
    Long securitiesFirmId = request.getSecuritiesFirmId();
    String plainAccountNumber = request.getAccountNumber();

    if (investorId == null || securitiesFirmId == null || plainAccountNumber == null) {
      throw new SecuritiesAccountValidationException(
          "investorId, securitiesFirmId, accountNumber는 필수 입력값입니다.");
    }

    Investor investor =
        investorRepository
            .findById(investorId)
            .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 투자자입니다"));

    ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

    securitiesFirmRepository
        .findByIdAndDeletedAtIsNull(securitiesFirmId)
        .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 증권사입니다"));

    // D-SA-003: 투자자의 첫 번째 계좌 등록 시 요청의 isDefault와 무관하게 true로 강제
    boolean firstAccount = securitiesAccountRepository.findByInvestorId(investorId).isEmpty();
    boolean isDefault = firstAccount || Boolean.TRUE.equals(request.getIsDefault());

    LocalDateTime now = LocalDateTime.now(clock);
    SecuritiesAccount entity =
        SecuritiesAccount.builder()
            .investorId(investorId)
            .securitiesFirmId(securitiesFirmId)
            .accountNumber(accountNumberEncryptor.encrypt(plainAccountNumber))
            .accountName(request.getAccountName())
            .isDefault(isDefault)
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    SecuritiesAccount saved = securitiesAccountRepository.save(entity);
    // 저장 직후이므로 저장에 사용한 원본 평문을 그대로 마스킹에 사용한다 (다시 복호화할 필요 없음)
    return SecuritiesAccountResponse.from(saved, AccountNumberMasker.mask(plainAccountNumber));
  }

  /**
   * 증권계좌 목록을 조회한다.
   */
  @Transactional(readOnly = true)
  public List<SecuritiesAccountResponse> list(Long investorId, String status) {
    if (investorId == null) {
      throw new SecuritiesAccountValidationException("investorId는 필수 입력값입니다.");
    }
    String normalized = normalizeStatus(status);

    Investor investor =
        investorRepository
            .findById(investorId)
            .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 투자자입니다"));

    ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

    List<SecuritiesAccount> accounts =
        "BOTH".equals(normalized)
            ? securitiesAccountRepository.findByInvestorId(investorId)
            : securitiesAccountRepository.findByInvestorIdAndStatus(
                investorId, RecordStatus.valueOf(normalized));

    return accounts.stream().map(this::toMaskedResponse).toList();
  }

  /**
   * ID로 증권계좌를 조회한다.
   */
  @Transactional(readOnly = true)
  public SecuritiesAccountResponse getById(Long id) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

    return toMaskedResponse(account);
  }

  /**
   * 복호화한 계좌번호 전체를 반환한다. 소유권을 검증한다.
   */
  @Transactional(readOnly = true)
  public SecuritiesAccountAccountNumberResponse getAccountNumber(Long id) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    Long currentAccountId =
        ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

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

  /**
   * 증권계좌 정보를 수정한다.
   */
  @Transactional
  public SecuritiesAccountResponse update(Long id, SecuritiesAccountUpdateRequest request) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

    if (request.getSecuritiesFirmId() != null) {
      SecuritiesFirm firm =
          securitiesFirmRepository
              .findById(request.getSecuritiesFirmId())
              .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 증권사입니다"));
      if (firm.getStatus() != RecordStatus.ACTIVE) {
        throw new SecuritiesAccountValidationException("비활성화된 증권사로는 변경할 수 없습니다.");
      }
      account.setSecuritiesFirmId(request.getSecuritiesFirmId());
    }

    if (request.getAccountName() != null) {
      account.setAccountName(request.getAccountName());
    }

    account.setUpdatedAt(LocalDateTime.now(clock));
    SecuritiesAccount saved = securitiesAccountRepository.save(account);

    return toMaskedResponse(saved);
  }

  /**
   * 증권계좌를 비활성화(소프트 삭제)한다.
   */
  @Transactional
  public void deactivate(Long id) {
    SecuritiesAccount account =
        securitiesAccountRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

    LocalDateTime now = LocalDateTime.now(clock);
    account.setStatus(RecordStatus.INACTIVE);
    account.setDeletedAt(now);
    // D-SA-004: 비활성화 대상이 기본 계좌였다면 함께 clear해 "INACTIVE인데 isDefault=true" 상태를 만들지 않는다
    if (Boolean.TRUE.equals(account.getIsDefault())) {
      account.setIsDefault(false);
    }
    account.setUpdatedAt(now);
    securitiesAccountRepository.save(account);
  }

  /**
   * 지정한 계좌를 투자자의 기본 계좌로 설정한다.
   */
  @Transactional
  public SecuritiesAccountResponse setDefault(Long id) {
    // D-SA-006: findByIdAndDeletedAtIsNull이 아닌 findById를 사용한다 — INACTIVE(soft-deleted) 계좌도
    // "존재는 하지만 비활성"으로 취급해 404가 아닌 400(AC-016)을 반환해야 하기 때문
    SecuritiesAccount account =
        securitiesAccountRepository
            .findById(id)
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    Investor investor =
        investorRepository
            .findById(account.getInvestorId())
            .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

    ownershipGuard.requireOwner(investor, SecuritiesAccountService::forbidden);

    if (account.getStatus() != RecordStatus.ACTIVE) {
      throw new SecuritiesAccountValidationException("비활성화된 계좌는 기본 계좌로 지정할 수 없습니다.");
    }

    // AC-015: 이미 기본 계좌면 트랜잭션을 시작하지 않고 즉시 409
    if (Boolean.TRUE.equals(account.getIsDefault())) {
      throw new SecuritiesAccountConflictException("이미 기본 계좌로 지정되어 있습니다.");
    }

    LocalDateTime now = LocalDateTime.now(clock);
    try {
      // 기존 기본 계좌 해제를 먼저 flush해 신규 지정 UPDATE와 동시에 커밋되지 않게 한다.
      // (Hibernate의 dirty-checking 플러시 순서는 호출 순서를 보장하지 않아, flush 없이는
      // 두 UPDATE가 뒤바뀌어 순간적으로 is_default=true가 2건이 되며 partial unique index 위반 발생)
      securitiesAccountRepository
          .findByInvestorIdAndIsDefaultTrue(account.getInvestorId())
          .ifPresent(
              previousDefault -> {
                previousDefault.setIsDefault(false);
                previousDefault.setUpdatedAt(now);
                securitiesAccountRepository.saveAndFlush(previousDefault);
              });

      account.setIsDefault(true);
      account.setUpdatedAt(now);
      SecuritiesAccount saved = securitiesAccountRepository.saveAndFlush(account);
      return toMaskedResponse(saved);
    } catch (DataIntegrityViolationException e) {
      // D-SA-006: 명시적 락 대신 partial unique index(uk_securities_account_default)에 의존한다.
      // 동시 요청으로 유니크 위반이 실제 발생하면 409로 변환하는 방어 코드
      throw new SecuritiesAccountConflictException("이미 기본 계좌로 지정되어 있습니다.");
    }
  }

  private SecuritiesAccountResponse toMaskedResponse(SecuritiesAccount account) {
    String plainAccountNumber = accountNumberEncryptor.decrypt(account.getAccountNumber());
    String maskedAccountNumber = AccountNumberMasker.mask(plainAccountNumber);
    // 표시용 조회이므로 findById(deletedAt 필터 없음)를 쓴다 - 증권사가 비활성화돼도
    // 기존에 연결된 계좌는 그대로 유지되므로(architecture.md), 이름도 계속 보여야 한다.
    String securitiesFirmName =
        securitiesFirmRepository
            .findById(account.getSecuritiesFirmId())
            .map(SecuritiesFirm::getName)
            .orElse(null);
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

  private static SecuritiesAccountForbiddenException forbidden() {
    return new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
  }
}
