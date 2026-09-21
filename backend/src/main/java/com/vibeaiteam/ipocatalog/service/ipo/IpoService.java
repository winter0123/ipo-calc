package com.vibeaiteam.ipocatalog.service.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoConflictException;
import com.vibeaiteam.ipocatalog.exception.IpoNotFoundException;
import com.vibeaiteam.ipocatalog.exception.IpoValidationException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IpoService {

    private static final int NAME_MAX_LENGTH = 255;
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final IpoRepository ipoRepository;

    @Transactional(readOnly = true)
    public List<IpoResponse> list() {
        return ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(IpoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IpoResponse getById(Long id) {
        Ipo ipo = ipoRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IpoNotFoundException("IPO를 찾을 수 없습니다."));
        return IpoResponse.from(ipo);
    }

    @Transactional
    public IpoResponse update(Long id, IpoUpdateRequest request) {
        Ipo ipo = ipoRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IpoNotFoundException("IPO를 찾을 수 없습니다."));

        String updatedCompanyName = request.getCompanyName();
        String updatedIpoName = request.getIpoName();
        String updatedStockCodeRaw = request.getStockCode();
        String updatedRoundLabel = request.getRoundLabel();
        Long updatedLowerBandPrice = request.getLowerBandPrice();
        Long updatedUpperBandPrice = request.getUpperBandPrice();
        Long updatedFixedPrice = request.getFixedPrice();
        LocalDate updatedSubscriptionStartDate = request.getSubscriptionStartDate();
        LocalDate updatedSubscriptionEndDate = request.getSubscriptionEndDate();
        LocalDate updatedListingDate = request.getListingDate();
        Long updatedOfferingShareCount = request.getOfferingShareCount();

        String computedCompanyName = updatedCompanyName != null ? updatedCompanyName.trim() : ipo.getCompanyName();
        String computedIpoName = updatedIpoName != null ? updatedIpoName.trim() : ipo.getIpoName();
        String computedStockCode = updatedStockCodeRaw != null ? normalizeOptional(updatedStockCodeRaw) : ipo.getStockCode();
        String computedRoundLabel = updatedRoundLabel != null ? normalizeOptional(updatedRoundLabel) : ipo.getRoundLabel();
        LocalDate computedSubscriptionStartDate = updatedSubscriptionStartDate != null ? updatedSubscriptionStartDate : ipo.getSubscriptionStartDate();
        LocalDate computedSubscriptionEndDate = updatedSubscriptionEndDate != null ? updatedSubscriptionEndDate : ipo.getSubscriptionEndDate();
        Long computedLowerBandPrice = updatedLowerBandPrice != null ? updatedLowerBandPrice : ipo.getLowerBandPrice();
        Long computedUpperBandPrice = updatedUpperBandPrice != null ? updatedUpperBandPrice : ipo.getUpperBandPrice();
        Long computedFixedPrice = updatedFixedPrice != null ? updatedFixedPrice : ipo.getFixedPrice();
        Long computedOfferingShareCount = updatedOfferingShareCount != null ? updatedOfferingShareCount : ipo.getOfferingShareCount();

        if (updatedCompanyName != null) {
            validateRequiredName("companyName", updatedCompanyName);
        }
        if (updatedIpoName != null) {
            validateRequiredName("ipoName", updatedIpoName);
        }
        if (updatedSubscriptionStartDate != null || updatedSubscriptionEndDate != null) {
            if (computedSubscriptionStartDate == null || computedSubscriptionEndDate == null) {
                throw new IpoValidationException("subscriptionStartDate와 subscriptionEndDate는 필수입니다.");
            }
            if (computedSubscriptionEndDate.isBefore(computedSubscriptionStartDate)) {
                throw new IpoValidationException("subscriptionEndDate는 subscriptionStartDate보다 빠를 수 없습니다.");
            }
        }
        if (updatedLowerBandPrice != null) {
            validatePrice("lowerBandPrice", updatedLowerBandPrice);
        }
        if (updatedUpperBandPrice != null) {
            validatePrice("upperBandPrice", updatedUpperBandPrice);
        }
        if (updatedFixedPrice != null) {
            validatePrice("fixedPrice", updatedFixedPrice);
        }
        if (computedLowerBandPrice != null && computedUpperBandPrice != null && computedLowerBandPrice > computedUpperBandPrice) {
            throw new IpoValidationException("lowerBandPrice는 upperBandPrice보다 클 수 없습니다.");
        }
        if (updatedOfferingShareCount != null) {
            if (updatedOfferingShareCount <= 0) {
                throw new IpoValidationException("offeringShareCount는 0보다 커야 합니다.");
            }
        }

        checkDuplicate(computedStockCode, computedIpoName, computedSubscriptionStartDate, id);

        if (updatedCompanyName != null) {
            ipo.setCompanyName(computedCompanyName);
        }
        if (updatedIpoName != null) {
            ipo.setIpoName(computedIpoName);
        }
        if (updatedStockCodeRaw != null) {
            ipo.setStockCode(computedStockCode);
        }
        if (updatedRoundLabel != null) {
            ipo.setRoundLabel(computedRoundLabel);
        }
        if (updatedSubscriptionStartDate != null || updatedSubscriptionEndDate != null) {
            ipo.setSubscriptionStartDate(computedSubscriptionStartDate);
            ipo.setSubscriptionEndDate(computedSubscriptionEndDate);
        }
        if (updatedLowerBandPrice != null) {
            ipo.setLowerBandPrice(computedLowerBandPrice);
        }
        if (updatedUpperBandPrice != null) {
            ipo.setUpperBandPrice(computedUpperBandPrice);
        }
        if (updatedFixedPrice != null) {
            ipo.setFixedPrice(computedFixedPrice);
        }
        if (updatedListingDate != null) {
            ipo.setListingDate(updatedListingDate);
        }
        if (updatedOfferingShareCount != null) {
            ipo.setOfferingShareCount(computedOfferingShareCount);
        }

        ipo.setUpdatedAt(LocalDateTime.now());
        try {
            Ipo saved = ipoRepository.save(ipo);
            return IpoResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IpoConflictException("이미 등록된 IPO입니다.");
        }
    }

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
