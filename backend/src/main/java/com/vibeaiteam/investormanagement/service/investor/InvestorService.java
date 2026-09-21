package com.vibeaiteam.investormanagement.service.investor;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorRegisterRequest;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorResponse;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.exception.InvestorValidationException;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InvestorService {

    private static final int DISPLAY_NAME_MAX_LENGTH = 50;

    private final InvestorRepository investorRepository;
    private final SecurityUtils securityUtils;

    public InvestorResponse register(InvestorRegisterRequest request) {
        Long accountId = securityUtils.getCurrentAccountId();
        String displayName = validateDisplayName(request.getDisplayName());

        LocalDateTime now = LocalDateTime.now();
        Investor investor = Investor.builder()
                .accountId(accountId)
                .displayName(displayName)
                .relationship(request.getRelationship())
                .status("ACTIVE")
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Investor saved = investorRepository.save(investor);
        return InvestorResponse.from(saved);
    }

    private String validateDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new InvestorValidationException("표시 이름은 필수입니다.");
        }
        if (displayName.trim().length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new InvestorValidationException("표시 이름은 최대 50자까지 가능합니다.");
        }
        return displayName.trim();
    }
}