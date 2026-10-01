package com.vibeaiteam.ipocatalog.service.ipo;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoRegisterRequest;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoConflictException;
import com.vibeaiteam.ipocatalog.exception.IpoNotFoundException;
import com.vibeaiteam.ipocatalog.exception.IpoValidationException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공모주 관리 서비스.
 */
@Service
@RequiredArgsConstructor
public class IpoService {

  private static final int NAME_MAX_LENGTH = 255;

  private final IpoRepository ipoRepository;
  private final Clock clock;

  /**
   * 공모주 목록을 조회한다.
   */
  @Transactional(readOnly = true)
  public List<IpoResponse> list() {
    return ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
        .map(IpoResponse::from)
        .toList();
  }

  /**
   * ID로 공모주를 조회한다.
   */
  @Transactional(readOnly = true)
  public IpoResponse getById(Long id) {
    Ipo ipo = findActive(id);
    return IpoResponse.from(ipo);
  }

  /**
   * 공모주를 비활성화(소프트 삭제)한다.
   */
  @Transactional
  public void deactivate(Long id) {
    Ipo ipo = findActive(id);
    ipo.setStatus(RecordStatus.INACTIVE);
    ipo.setDeletedAt(LocalDateTime.now(clock));
    ipo.setUpdatedAt(LocalDateTime.now(clock));
    ipoRepository.save(ipo);
  }

  /**
   * 공모주 정보를 수정한다.
   */
  @Transactional
  public IpoResponse update(Long id, IpoUpdateRequest request) {
    Ipo ipo = findActive(id);

    final String updatedCompanyName = request.getCompanyName();
    final String updatedIpoName = request.getIpoName();
    final String updatedStockCodeRaw = request.getStockCode();
    final String updatedRoundLabel = request.getRoundLabel();
    final Long updatedLowerBandPrice = request.getLowerBandPrice();
    final Long updatedUpperBandPrice = request.getUpperBandPrice();
    final Long updatedFixedPrice = request.getFixedPrice();
    final LocalDate updatedSubscriptionStartDate = request.getSubscriptionStartDate();
    final LocalDate updatedSubscriptionEndDate = request.getSubscriptionEndDate();
    final Long updatedOfferingShareCount = request.getOfferingShareCount();

    final String computedCompanyName =
        updatedCompanyName != null ? updatedCompanyName.trim() : ipo.getCompanyName();
    final String computedIpoName =
        updatedIpoName != null ? updatedIpoName.trim() : ipo.getIpoName();
    final String computedStockCode =
        updatedStockCodeRaw != null ? normalizeOptional(updatedStockCodeRaw) : ipo.getStockCode();
    final String computedRoundLabel =
        updatedRoundLabel != null ? normalizeOptional(updatedRoundLabel) : ipo.getRoundLabel();
    final LocalDate computedSubscriptionStartDate =
        pick(updatedSubscriptionStartDate, ipo.getSubscriptionStartDate());
    final LocalDate computedSubscriptionEndDate =
        pick(updatedSubscriptionEndDate, ipo.getSubscriptionEndDate());
    final Long computedLowerBandPrice = pick(updatedLowerBandPrice, ipo.getLowerBandPrice());
    final Long computedUpperBandPrice = pick(updatedUpperBandPrice, ipo.getUpperBandPrice());

    validateUpdate(request, ipo);

    checkDuplicate(computedStockCode, computedIpoName, computedSubscriptionStartDate, id);

    // 미입력 필드는 현재 값을 그대로 다시 설정하므로 변경이 없다
    ipo.setCompanyName(computedCompanyName);
    ipo.setIpoName(computedIpoName);
    ipo.setStockCode(computedStockCode);
    ipo.setRoundLabel(computedRoundLabel);
    ipo.setSubscriptionStartDate(computedSubscriptionStartDate);
    ipo.setSubscriptionEndDate(computedSubscriptionEndDate);
    ipo.setLowerBandPrice(computedLowerBandPrice);
    ipo.setUpperBandPrice(computedUpperBandPrice);
    ipo.setFixedPrice(pick(updatedFixedPrice, ipo.getFixedPrice()));
    ipo.setListingDate(pick(request.getListingDate(), ipo.getListingDate()));
    ipo.setOfferingShareCount(pick(updatedOfferingShareCount, ipo.getOfferingShareCount()));

    ipo.setUpdatedAt(LocalDateTime.now(clock));
    try {
      Ipo saved = ipoRepository.save(ipo);
      return IpoResponse.from(saved);
    } catch (DataIntegrityViolationException ex) {
      throw new IpoConflictException("이미 등록된 IPO입니다.");
    }
  }

  /**
   * 수정 요청에 대한 검증을 수행한다. 검증 실패 시 {@link IpoValidationException}을 던진다.
   *
   * @param request 수정 요청
   * @param ipo 기존 IPO 엔티티
   */
  private void validateUpdate(IpoUpdateRequest request, Ipo ipo) {
    final String updatedCompanyName = request.getCompanyName();
    final String updatedIpoName = request.getIpoName();
    final LocalDate updatedSubscriptionStartDate = request.getSubscriptionStartDate();
    final LocalDate updatedSubscriptionEndDate = request.getSubscriptionEndDate();
    final Long updatedLowerBandPrice = request.getLowerBandPrice();
    final Long updatedUpperBandPrice = request.getUpperBandPrice();
    final Long updatedFixedPrice = request.getFixedPrice();
    final Long updatedOfferingShareCount = request.getOfferingShareCount();

    final LocalDate computedSubscriptionStartDate =
        pick(updatedSubscriptionStartDate, ipo.getSubscriptionStartDate());
    final LocalDate computedSubscriptionEndDate =
        pick(updatedSubscriptionEndDate, ipo.getSubscriptionEndDate());
    final Long computedLowerBandPrice = pick(updatedLowerBandPrice, ipo.getLowerBandPrice());
    final Long computedUpperBandPrice = pick(updatedUpperBandPrice, ipo.getUpperBandPrice());

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
    if (computedLowerBandPrice != null
        && computedUpperBandPrice != null
        && computedLowerBandPrice > computedUpperBandPrice) {
      throw new IpoValidationException("lowerBandPrice는 upperBandPrice보다 클 수 없습니다.");
    }
    if (updatedOfferingShareCount != null) {
      if (updatedOfferingShareCount <= 0) {
        throw new IpoValidationException("offeringShareCount는 0보다 커야 합니다.");
      }
    }
  }

  /**
   * 공모주를 등록한다.
   */
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

    LocalDateTime now = LocalDateTime.now(clock);
    Ipo ipo =
        Ipo.builder()
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
            .status(RecordStatus.ACTIVE)
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

  private Ipo findActive(Long id) {
    return ipoRepository
        .findByIdAndDeletedAtIsNull(id)
        .orElseThrow(() -> new IpoNotFoundException("IPO를 찾을 수 없습니다."));
  }

  private static <T> T pick(T updated, T current) {
    return updated != null ? updated : current;
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

  private void checkDuplicate(
      String stockCode, String ipoName, LocalDate startDate, Long excludeId) {
    boolean duplicate;
    if (stockCode != null) {
      duplicate =
          ipoRepository
              .findByStockCodeAndStatusAndDeletedAtIsNull(stockCode, RecordStatus.ACTIVE)
              .filter(existing -> !existing.getId().equals(excludeId))
              .isPresent();
    } else {
      duplicate =
          ipoRepository
              .findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
                  ipoName, startDate, RecordStatus.ACTIVE)
              .filter(existing -> !existing.getId().equals(excludeId))
              .isPresent();
    }

    if (duplicate) {
      throw new IpoConflictException("이미 등록된 IPO입니다.");
    }
  }
}
