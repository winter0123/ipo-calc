package com.vibeaiteam.costrecording.service.cost;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.costrecording.controller.cost.dto.CostRegisterRequest;
import com.vibeaiteam.costrecording.controller.cost.dto.CostResponse;
import com.vibeaiteam.costrecording.controller.cost.exception.CostForbiddenException;
import com.vibeaiteam.costrecording.controller.cost.exception.CostNotFoundException;
import com.vibeaiteam.costrecording.controller.cost.exception.CostValidationException;
import com.vibeaiteam.costrecording.domain.model.cost.Cost;
import com.vibeaiteam.costrecording.repository.cost.CostRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CostService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final CostRepository costRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvestorRepository investorRepository;
    private final SecurityUtils securityUtils;

    // 비용을 등록한다. subscriptionId 존재 검증(404), 계정 소유권 검증(403), amount 0 이상 검증(400)을 수행하고,
    // status=ACTIVE로 새 Cost를 생성해 저장한다.
    @Transactional
    public CostResponse register(CostRegisterRequest request) {
        validateRequest(request);

        Long subscriptionId = request.getSubscriptionId();

        // 1. Subscription 존재 검증 (deletedAt IS NULL, CANCELLED 제외) — 404
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(subscriptionId)
                .orElseThrow(() -> new CostNotFoundException("청약 기록을 찾을 수 없습니다."));

        // 2. 계정 소유권 검증 — Subscription → Investor → Account 체인으로 확인 — 403
        validateSubscriptionOwnership(subscription);

        // 3. Cost 생성 및 저장 (status=ACTIVE)
        LocalDateTime now = LocalDateTime.now();
        Cost cost = Cost.builder()
                .subscriptionId(subscriptionId)
                .costType(request.getCostType())
                .amount(request.getAmount())
                .incurredAt(request.getIncurredAt())
                .memo(request.getMemo())
                .status(STATUS_ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Cost saved = costRepository.save(cost);
        return CostResponse.from(saved);
    }

    // 요청 필수값 검증: subscriptionId, costType, amount, incurredAt 누락 시 400
    // amount 0 미만 시 400, memo 255자 초과 시 400
    private void validateRequest(CostRegisterRequest request) {
        if (request == null) {
            throw new CostValidationException("비용 등록 정보는 필수입니다.");
        }

        if (request.getSubscriptionId() == null
                || request.getCostType() == null
                || request.getAmount() == null
                || request.getIncurredAt() == null) {
            throw new CostValidationException("subscriptionId, costType, amount, incurredAt는 필수입니다.");
        }

        if (request.getAmount() < 0) {
            throw new CostValidationException("amount는 0 이상이어야 합니다.");
        }

        if (request.getMemo() != null && request.getMemo().length() > 255) {
            throw new CostValidationException("memo는 255자 이하여야 합니다.");
        }
    }

    // Subscription 소유권 검증: Subscription → Investor(accountId) → 현재 계정 ID 비교
    private void validateSubscriptionOwnership(Subscription subscription) {
        Long currentAccountId = securityUtils.getCurrentAccountId();
        Investor investor = investorRepository.findById(subscription.getInvestorId())
                .orElseThrow(() -> new CostNotFoundException("투자자를 찾을 수 없습니다."));
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new CostForbiddenException("계정 소유권이 없습니다.");
        }
    }
}