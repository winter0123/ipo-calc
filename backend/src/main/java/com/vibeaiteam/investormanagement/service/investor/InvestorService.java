package com.vibeaiteam.investormanagement.service.investor;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorRegisterRequest;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorResponse;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.exception.InvestorForbiddenException;
import com.vibeaiteam.investormanagement.exception.InvestorNotFoundException;
import com.vibeaiteam.investormanagement.exception.InvestorValidationException;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InvestorService {

    private static final int DISPLAY_NAME_MAX_LENGTH = 50;
    private static final Set<String> SUPPORTED_STATUS = Set.of("ACTIVE", "INACTIVE", "BOTH");
    private static final String DEFAULT_STATUS = "ACTIVE";

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

    public List<InvestorResponse> list(String status) {
        Long accountId = securityUtils.getCurrentAccountId();
        String normalized = normalizeStatus(status);
        List<Investor> investors = "BOTH".equals(normalized)
                ? investorRepository.findByAccountId(accountId)
                : investorRepository.findByAccountIdAndStatus(accountId, normalized);
        return investors.stream()
                .map(InvestorResponse::from)
                .toList();
    }

    public InvestorResponse getById(Long id) {
        Long accountId = securityUtils.getCurrentAccountId();
        Investor investor = investorRepository.findById(id)
                .orElseThrow(() -> new InvestorNotFoundException("투자자를 찾을 수 없습니다."));
        if (!accountId.equals(investor.getAccountId())) {
            throw new InvestorForbiddenException("계정 소유권이 없습니다.");
        }
        return InvestorResponse.from(investor);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return DEFAULT_STATUS;
        }
        String upper = status.trim().toUpperCase();
        if (!SUPPORTED_STATUS.contains(upper)) {
            throw new InvestorValidationException("status는 ACTIVE, INACTIVE, BOTH 중 하나여야 합니다.");
        }
        return upper;
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
