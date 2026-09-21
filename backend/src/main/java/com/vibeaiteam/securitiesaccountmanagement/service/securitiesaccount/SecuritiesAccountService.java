package com.vibeaiteam.securitiesaccountmanagement.service.securitiesaccount;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountAccountNumberResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountUpdateRequest;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountForbiddenException;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountNotFoundException;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountValidationException;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecuritiesAccountService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_STATUS = STATUS_ACTIVE;
    private static final Set<String> SUPPORTED_STATUS = Set.of("ACTIVE", "INACTIVE", "BOTH");

    private final InvestorRepository investorRepository;
    private final SecuritiesFirmRepository securitiesFirmRepository;
    private final SecuritiesAccountRepository securitiesAccountRepository;
    private final AccountNumberEncryptor accountNumberEncryptor;
    private final SecurityUtils securityUtils;

    @Transactional
    public SecuritiesAccountResponse register(SecuritiesAccountRegisterRequest request) {
        Long investorId = request.getInvestorId();
        Long securitiesFirmId = request.getSecuritiesFirmId();
        String plainAccountNumber = request.getAccountNumber();

        if (investorId == null || securitiesFirmId == null || plainAccountNumber == null) {
            throw new SecuritiesAccountValidationException(
                    "investorId, securitiesFirmId, accountNumber는 필수 입력값입니다.");
        }

        Investor investor = investorRepository.findById(investorId)
                .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 투자자입니다"));

        Long currentAccountId = securityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
        }

        securitiesFirmRepository.findByIdAndDeletedAtIsNull(securitiesFirmId)
                .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 증권사입니다"));

        // D-SA-003: 투자자의 첫 번째 계좌 등록 시 요청의 isDefault와 무관하게 true로 강제
        boolean firstAccount = securitiesAccountRepository.findByInvestorId(investorId).isEmpty();
        boolean isDefault = firstAccount || Boolean.TRUE.equals(request.getIsDefault());

        LocalDateTime now = LocalDateTime.now();
        SecuritiesAccount entity = SecuritiesAccount.builder()
                .investorId(investorId)
                .securitiesFirmId(securitiesFirmId)
                .accountNumber(accountNumberEncryptor.encrypt(plainAccountNumber))
                .accountName(request.getAccountName())
                .isDefault(isDefault)
                .status(STATUS_ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        SecuritiesAccount saved = securitiesAccountRepository.save(entity);
        // 저장 직후이므로 저장에 사용한 원본 평문을 그대로 마스킹에 사용한다 (다시 복호화할 필요 없음)
        return SecuritiesAccountResponse.from(saved, AccountNumberMasker.mask(plainAccountNumber));
    }

    @Transactional(readOnly = true)
    public List<SecuritiesAccountResponse> list(Long investorId, String status) {
        if (investorId == null) {
            throw new SecuritiesAccountValidationException("investorId는 필수 입력값입니다.");
        }
        String normalized = normalizeStatus(status);

        Investor investor = investorRepository.findById(investorId)
                .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 투자자입니다"));

        Long currentAccountId = securityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
        }

        List<SecuritiesAccount> accounts = "BOTH".equals(normalized)
                ? securitiesAccountRepository.findByInvestorId(investorId)
                : securitiesAccountRepository.findByInvestorIdAndStatus(investorId, normalized);

        return accounts.stream()
                .map(this::toMaskedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SecuritiesAccountResponse getById(Long id) {
        SecuritiesAccount account = securitiesAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Investor investor = investorRepository.findById(account.getInvestorId())
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Long currentAccountId = securityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
        }

        return toMaskedResponse(account);
    }

    @Transactional(readOnly = true)
    public SecuritiesAccountAccountNumberResponse getAccountNumber(Long id) {
        SecuritiesAccount account = securitiesAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Investor investor = investorRepository.findById(account.getInvestorId())
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Long currentAccountId = securityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
        }

        String plainAccountNumber = accountNumberEncryptor.decrypt(account.getAccountNumber());
        Instant exposedAt = Instant.now();

        // D-SA-005: 감사 로그는 SLF4J 애플리케이션 로그로만 기록 (별도 DB 테이블 없음)
        log.info("계좌번호 노출: accountId={}, requestedByAccountId={}, exposedAt={}",
                id, currentAccountId, exposedAt);

        return SecuritiesAccountAccountNumberResponse.of(id, plainAccountNumber, exposedAt);
    }

    @Transactional
    public SecuritiesAccountResponse update(Long id, SecuritiesAccountUpdateRequest request) {
        SecuritiesAccount account = securitiesAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Investor investor = investorRepository.findById(account.getInvestorId())
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Long currentAccountId = securityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
        }

        if (request.getSecuritiesFirmId() != null) {
            SecuritiesFirm firm = securitiesFirmRepository.findById(request.getSecuritiesFirmId())
                    .orElseThrow(() -> new SecuritiesAccountValidationException("존재하지 않는 증권사입니다"));
            if (!STATUS_ACTIVE.equals(firm.getStatus())) {
                throw new SecuritiesAccountValidationException("비활성화된 증권사로는 변경할 수 없습니다.");
            }
            account.setSecuritiesFirmId(request.getSecuritiesFirmId());
        }

        if (request.getAccountName() != null) {
            account.setAccountName(request.getAccountName());
        }

        account.setUpdatedAt(LocalDateTime.now());
        SecuritiesAccount saved = securitiesAccountRepository.save(account);

        return toMaskedResponse(saved);
    }

    @Transactional
    public void deactivate(Long id) {
        SecuritiesAccount account = securitiesAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Investor investor = investorRepository.findById(account.getInvestorId())
                .orElseThrow(() -> new SecuritiesAccountNotFoundException("증권계좌를 찾을 수 없습니다."));

        Long currentAccountId = securityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SecuritiesAccountForbiddenException("계정 소유권이 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        account.setStatus("INACTIVE");
        account.setDeletedAt(now);
        // D-SA-004: 비활성화 대상이 기본 계좌였다면 함께 clear해 "INACTIVE인데 isDefault=true" 상태를 만들지 않는다
        if (Boolean.TRUE.equals(account.getIsDefault())) {
            account.setIsDefault(false);
        }
        account.setUpdatedAt(now);
        securitiesAccountRepository.save(account);
    }

    private SecuritiesAccountResponse toMaskedResponse(SecuritiesAccount account) {
        String plainAccountNumber = accountNumberEncryptor.decrypt(account.getAccountNumber());
        String maskedAccountNumber = AccountNumberMasker.mask(plainAccountNumber);
        // 표시용 조회이므로 findById(deletedAt 필터 없음)를 쓴다 - 증권사가 비활성화돼도
        // 기존에 연결된 계좌는 그대로 유지되므로(architecture.md), 이름도 계속 보여야 한다.
        String securitiesFirmName = securitiesFirmRepository.findById(account.getSecuritiesFirmId())
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
}