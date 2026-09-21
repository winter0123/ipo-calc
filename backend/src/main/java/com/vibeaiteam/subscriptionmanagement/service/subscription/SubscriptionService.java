package com.vibeaiteam.subscriptionmanagement.service.subscription;

import com.vibeaiteam.accountauthentication.util.security.SecurityUtils;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionRegisterRequest;
import com.vibeaiteam.subscriptionmanagement.controller.subscription.dto.SubscriptionResponse;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionConflictException;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionForbiddenException;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionNotFoundException;
import com.vibeaiteam.subscriptionmanagement.exception.SubscriptionValidationException;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SubscriptionRepository subscriptionRepository;
    private final InvestorRepository investorRepository;
    private final SecuritiesAccountRepository securitiesAccountRepository;
    private final IpoRepository ipoRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public SubscriptionResponse register(SubscriptionRegisterRequest request) {
        validateRequest(request);

        Long investorId = request.getInvestorId();
        Long securitiesAccountId = request.getSecuritiesAccountId();
        Long ipoId = request.getIpoId();
        Long currentAccountId = securityUtils.getCurrentAccountId();

        // Investor 존재/소유권 검증 — InvestorService.findOwnedInvestor와 동일 패턴(없으면 404, 타 계정 소유면 403)
        Investor investor = investorRepository.findById(investorId)
                .orElseThrow(() -> new SubscriptionNotFoundException("투자자를 찾을 수 없습니다."));
        if (!currentAccountId.equals(investor.getAccountId())) {
            throw new SubscriptionForbiddenException("계정 소유권이 없습니다.");
        }

        // SecuritiesAccount 존재/2단계 소유권 검증 — SecuritiesAccountService.getById 패턴(존재+미삭제 404)
        SecuritiesAccount account = securitiesAccountRepository.findByIdAndDeletedAtIsNull(securitiesAccountId)
                .orElseThrow(() -> new SubscriptionNotFoundException("증권계좌를 찾을 수 없습니다."));
        Investor accountInvestor = investorRepository.findById(account.getInvestorId())
                .orElseThrow(() -> new SubscriptionNotFoundException("증권계좌를 찾을 수 없습니다."));
        if (!currentAccountId.equals(accountInvestor.getAccountId())) {
            throw new SubscriptionForbiddenException("계정 소유권이 없습니다.");
        }

        // IPO 존재 검증만 수행(전역 기준정보 — 계정과 무관, 삭제되지 않은 IPO만 허용)
        ipoRepository.findByIdAndDeletedAtIsNull(ipoId)
                .orElseThrow(() -> new SubscriptionNotFoundException("IPO를 찾을 수 없습니다."));

        checkDuplicate(investorId, securitiesAccountId, ipoId);

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
                .investorId(investorId)
                .securitiesAccountId(securitiesAccountId)
                .ipoId(ipoId)
                .quantity(request.getQuantity())
                .depositAmount(request.getDepositAmount())
                .subscribedAt(request.getSubscribedAt())
                .memo(request.getMemo())
                .status(STATUS_ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            Subscription saved = subscriptionRepository.save(subscription);
            return SubscriptionResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // AC-005: 부분 유니크 인덱스(uk_subscription_active_combo)가 최종 방어선.
            // Service 중복 검사를 놓친 동시 요청이 유니크 위반을 일으키면 409로 변환한다.
            throw new SubscriptionConflictException("이미 등록된 청약입니다.");
        }
    }

    // 등록된 청약 목록을 createdAt 내림차순으로 조회한다. investorId/securitiesAccountId가 주어지면 해당 조건으로
    // 필터링하고(둘 다 null이면 전체), 삭제되지 않은(ACTIVE) 기록만 반환한다. 둘 다 주어진 경우 investorId 우선
    // 조회 후 그 결과 안에서 securitiesAccountId를 다시 필터링한다. 조회 시 계정 소유권 검증은 수행하지 않는다.
    // (AC-007/008/009/018 — spec/architecture에 조회 시 소유권 검증이 명시되어 있지 않음)
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(Long investorId, Long securitiesAccountId) {
        List<Subscription> subscriptions;
        if (investorId != null) {
            subscriptions = subscriptionRepository
                    .findAllByInvestorIdAndDeletedAtIsNullOrderByCreatedAtDesc(investorId);
            if (securitiesAccountId != null) {
                subscriptions = subscriptions.stream()
                        .filter(subscription -> securitiesAccountId.equals(subscription.getSecuritiesAccountId()))
                        .toList();
            }
        } else if (securitiesAccountId != null) {
            subscriptions = subscriptionRepository
                    .findAllBySecuritiesAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(securitiesAccountId);
        } else {
            subscriptions = subscriptionRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return subscriptions.stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    // 청약 ID로 단건 청약을 조회한다. 삭제되지 않은(ACTIVE) 기록만 반환하고, 존재하지 않거나 취소된
    // 기록이면 404 SubscriptionNotFoundException을 던진다(AC-010/011). 조회 시 계정 소유권 검증은 수행하지
    // 않는다 — spec/architecture에 조회 시 소유권 검증이 명시되어 있지 않고, 등록(SF-2) 시에만 소유권을
    // 이미 강제했으므로 중복 검증하지 않는다. (IpoService.getById 패턴)
    @Transactional(readOnly = true)
    public SubscriptionResponse getById(Long id) {
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new SubscriptionNotFoundException("청약 기록을 찾을 수 없습니다."));
        return SubscriptionResponse.from(subscription);
    }

    private void validateRequest(SubscriptionRegisterRequest request) {
        if (request == null) {
            throw new SubscriptionValidationException("청약 등록 정보는 필수입니다.");
        }

        if (request.getInvestorId() == null
                || request.getSecuritiesAccountId() == null
                || request.getIpoId() == null
                || request.getQuantity() == null
                || request.getDepositAmount() == null
                || request.getSubscribedAt() == null) {
            throw new SubscriptionValidationException(
                    "investorId, securitiesAccountId, ipoId, quantity, depositAmount, subscribedAt는 필수입니다.");
        }

        if (request.getQuantity() <= 0) {
            throw new SubscriptionValidationException("quantity는 0보다 커야 합니다.");
        }
        if (request.getDepositAmount() < 0) {
            throw new SubscriptionValidationException("depositAmount는 음수일 수 없습니다.");
        }
    }

    private void checkDuplicate(Long investorId, Long securitiesAccountId, Long ipoId) {
        // AC-005: (investorId, securitiesAccountId, ipoId) 조합의 ACTIVE 기록이 이미 있으면 409 (AC-006: CANCELLED는 무관)
        subscriptionRepository
                .findByInvestorIdAndSecuritiesAccountIdAndIpoIdAndStatusAndDeletedAtIsNull(
                        investorId, securitiesAccountId, ipoId, STATUS_ACTIVE)
                .ifPresent(existing -> {
                    throw new SubscriptionConflictException("이미 등록된 청약입니다.");
                });
    }
}