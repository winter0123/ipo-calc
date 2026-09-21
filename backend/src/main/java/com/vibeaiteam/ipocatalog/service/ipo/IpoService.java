package com.vibeaiteam.ipocatalog.service.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoConflictException;
import com.vibeaiteam.ipocatalog.exception.IpoValidationException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IpoService {

    private static final int NAME_MAX_LENGTH = 255;
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final IpoRepository ipoRepository;

    @Transactional
    public IpoResponse register(IpoRegisterRequest request) {
        validateRequest(request);

        String companyName = request.getCompanyName().trim();
        String ipoName = request.getIpoName().trim();
        String stockCode = normalizeOptional(request.getStockCode());
        String roundLabel = normalizeOptional(request.getRoundLabel());
        LocalDate subscriptionStartDate = request.getSubscriptionStartDate();
        LocalDate subscriptionEndDate = request.getSubscriptionEndDate();

        checkDuplicate(stockCode, ipoName, subscriptionStartDate, null);

        LocalDateTime now = LocalDateTime.now();
        Ipo ipo = Ipo.builder()
                .companyName(companyName)
                .ipoName(ipoName)
                .stockCode(stockCode)
                .roundLabel(roundLabel)
                .lowerBandPrice(request.getLowerBandPrice())
                .upperBandPrice(request.getUpperBandPrice())
                .fixedPrice(request.getFixedPrice())
                .subscriptionStartDate(subscriptionStartDate)
                .subscriptionEndDate(subscriptionEndDate)
                .listingDate(request.getListingDate())
                .offeringShareCount(request.getOfferingShareCount())
                .status(STATUS_ACTIVE)
                .deletedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            Ipo saved = ipoRepository.save(ipo);
            return IpoResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IpoConflictException("이미 등록된 IPO입니다.");
        }
    }

    private void validateRequest(IpoRegisterRequest request) {
        if (request == null) {
            throw new IpoValidationException("IPO 등록 정보는 필수입니다.");
        }

        validateRequiredName("companyName", request.getCompanyName());
        validateRequiredName("ipoName", request.getIpoName());

        if (request.getSubscriptionStartDate() == null) {
            throw new IpoValidationException("subscriptionStartDate는 필수입니다.");
        }
        if (request.getSubscriptionEndDate() == null) {
            throw new IpoValidationException("subscriptionEndDate는 필수입니다.");
        }
        if (request.getSubscriptionEndDate().isBefore(request.getSubscriptionStartDate())) {
            throw new IpoValidationException("subscriptionEndDate는 subscriptionStartDate보다 빠를 수 없습니다.");
        }

        validatePrice("lowerBandPrice", request.getLowerBandPrice());
        validatePrice("upperBandPrice", request.getUpperBandPrice());
        validatePrice("fixedPrice", request.getFixedPrice());

        if (request.getLowerBandPrice() != null
                && request.getUpperBandPrice() != null
                && request.getLowerBandPrice() > request.getUpperBandPrice()) {
            throw new IpoValidationException("lowerBandPrice는 upperBandPrice보다 클 수 없습니다.");
        }

        if (request.getOfferingShareCount() != null && request.getOfferingShareCount() <= 0) {
            throw new IpoValidationException("offeringShareCount는 0보다 커야 합니다.");
        }
    }

    private void validateRequiredName(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IpoValidationException(fieldName + "는 필수입니다.");
        }
        if (value.trim().length() > NAME_MAX_LENGTH) {
            throw new IpoValidationException(fieldName + "는 최대 255자까지 가능합니다.");
        }
    }

    private void validatePrice(String fieldName, Long value) {
        if (value != null && value < 0) {
            throw new IpoValidationException(fieldName + "는 음수일 수 없습니다.");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void checkDuplicate(String stockCode, String ipoName, LocalDate startDate, Long excludeId) {
        boolean duplicate;
        if (stockCode != null) {
            duplicate = ipoRepository
                    .findByStockCodeAndStatusAndDeletedAtIsNull(stockCode, STATUS_ACTIVE)
                    .filter(existing -> !existing.getId().equals(excludeId))
                    .isPresent();
        } else {
            duplicate = ipoRepository
                    .findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
                            ipoName, startDate, STATUS_ACTIVE)
                    .filter(existing -> !existing.getId().equals(excludeId))
                    .isPresent();
        }

        if (duplicate) {
            throw new IpoConflictException("이미 등록된 IPO입니다.");
        }
    }
}
