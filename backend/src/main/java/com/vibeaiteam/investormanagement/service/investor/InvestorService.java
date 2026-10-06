package com.vibeaiteam.investormanagement.service.investor;

import com.vibeaiteam.accountauthentication.controller.dto.SuccessResponse;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorRegisterRequest;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorResponse;
import com.vibeaiteam.investormanagement.controller.investor.dto.InvestorUpdateRequest;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.exception.InvestorForbiddenException;
import com.vibeaiteam.investormanagement.exception.InvestorNotFoundException;
import com.vibeaiteam.investormanagement.exception.InvestorValidationException;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.investormanagement.service.ownership.OwnershipGuard;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 투자자 관리 서비스. */
@Service
@RequiredArgsConstructor
public class InvestorService {

  private static final int DISPLAY_NAME_MAX_LENGTH = 50;
  private static final Set<String> SUPPORTED_STATUS =
      Set.of(RecordStatus.ACTIVE.name(), RecordStatus.INACTIVE.name(), "BOTH");
  private static final String DEFAULT_STATUS = RecordStatus.ACTIVE.name();

  private final InvestorRepository investorRepository;
  private final SecurityUtils securityUtils;
  private final OwnershipGuard ownershipGuard;
  private final Clock clock;

  /** 투자자를 등록한다. */
  public InvestorResponse register(InvestorRegisterRequest request) {
    Long accountId = securityUtils.getCurrentAccountId();
    String displayName = validateDisplayName(request.getDisplayName());

    LocalDateTime now = LocalDateTime.now(clock);
    Investor investor =
        Investor.builder()
            .accountId(accountId)
            .displayName(displayName)
            .relationship(request.getRelationship())
            .status(RecordStatus.ACTIVE)
            .deletedAt(null)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Investor saved = investorRepository.save(investor);
    return InvestorResponse.from(saved);
  }

  /** 투자자 목록을 조회한다. */
  public List<InvestorResponse> list(String status) {
    Long accountId = securityUtils.getCurrentAccountId();
    String normalized = normalizeStatus(status);
    List<Investor> investors =
        "BOTH".equals(normalized)
            ? investorRepository.findByAccountId(accountId)
            : investorRepository.findByAccountIdAndStatus(
                accountId, RecordStatus.valueOf(normalized));
    return investors.stream().map(InvestorResponse::from).toList();
  }

  /** ID로 투자자를 조회한다. */
  public InvestorResponse getById(Long id) {
    Investor investor = findOwnedInvestor(id);
    return InvestorResponse.from(investor);
  }

  /** 투자자 정보를 수정한다. */
  public InvestorResponse update(Long id, InvestorUpdateRequest request) {
    Investor investor = findOwnedInvestor(id);

    if (request.getDisplayName() != null) {
      investor.setDisplayName(validateDisplayName(request.getDisplayName()));
    }
    if (request.getRelationship() != null) {
      investor.setRelationship(normalizeRelationship(request.getRelationship()));
    }
    investor.setUpdatedAt(LocalDateTime.now(clock));

    Investor saved = investorRepository.save(investor);
    return InvestorResponse.from(saved);
  }

  /** 투자자를 비활성화(소프트 삭제)한다. */
  public SuccessResponse deactivate(Long id) {
    Investor investor = findOwnedInvestor(id);
    if (investor.getStatus() != RecordStatus.INACTIVE) {
      LocalDateTime now = LocalDateTime.now(clock);
      investor.setStatus(RecordStatus.INACTIVE);
      investor.setDeletedAt(now);
      investor.setUpdatedAt(now);
      investorRepository.save(investor);
    }
    return SuccessResponse.of("투자자가 비활성화되었습니다.");
  }

  private Investor findOwnedInvestor(Long id) {
    return ownershipGuard.requireOwnedInvestor(
        id,
        () -> new InvestorNotFoundException("투자자를 찾을 수 없습니다."),
        () -> new InvestorForbiddenException("계정 소유권이 없습니다."));
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

  private String normalizeRelationship(String relationship) {
    if (relationship == null || relationship.isEmpty()) {
      return null;
    }
    return relationship;
  }
}
