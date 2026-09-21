package com.vibeaiteam.securitiesaccountmanagement.service.securitiesaccount;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountRegisterRequest;
import com.vibeaiteam.securitiesaccountmanagement.controller.securitiesaccount.dto.SecuritiesAccountResponse;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountForbiddenException;
import com.vibeaiteam.securitiesaccountmanagement.exception.SecuritiesAccountValidationException;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberMasker;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SecuritiesAccountService {

    private static final String STATUS_ACTIVE = "ACTIVE";

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
}