package com.vibeaiteam.securitiesfirmcatalog.service.securitiesfirm;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmRegisterRequest;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmResponse;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmUpdateRequest;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmDuplicateException;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmNotFoundException;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmValidationException;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 증권사 관리 서비스. */
@Service
@RequiredArgsConstructor
public class SecuritiesFirmService {

  private static final int NAME_MAX_LENGTH = 255;

  private final SecuritiesFirmRepository securitiesFirmRepository;
  private final Clock clock;

  /** 증권사를 등록한다. */
  public SecuritiesFirmResponse register(SecuritiesFirmRegisterRequest request) {
    String name = validateName(request.getName());

    securitiesFirmRepository
        .findByNameAndStatus(name, RecordStatus.ACTIVE)
        .ifPresent(
            existing -> {
              throw new SecuritiesFirmDuplicateException("이미 등록된 증권사명입니다.");
            });

    LocalDateTime now = LocalDateTime.now(clock);
    SecuritiesFirm securitiesFirm =
        SecuritiesFirm.builder()
            .name(name)
            .externalCode(request.getExternalCode())
            .businessRegistrationNumber(request.getBusinessRegistrationNumber())
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    SecuritiesFirm saved = securitiesFirmRepository.save(securitiesFirm);
    return SecuritiesFirmResponse.from(saved);
  }

  /** 증권사 목록을 조회한다. */
  public List<SecuritiesFirmResponse> list() {
    List<SecuritiesFirm> firms = securitiesFirmRepository.findByDeletedAtIsNull();
    return firms.stream().map(SecuritiesFirmResponse::from).toList();
  }

  /** ID로 증권사를 조회한다. */
  public SecuritiesFirmResponse getById(Long id) {
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesFirmNotFoundException("증권사를 찾을 수 없습니다."));
    return SecuritiesFirmResponse.from(firm);
  }

  /** 증권사 정보를 수정한다. */
  public SecuritiesFirmResponse update(Long id, SecuritiesFirmUpdateRequest request) {
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesFirmNotFoundException("증권사를 찾을 수 없습니다."));

    if (request.getName() != null) {
      String newName = validateName(request.getName());
      if (!newName.equals(firm.getName())) {
        securitiesFirmRepository
            .findByNameAndStatus(newName, RecordStatus.ACTIVE)
            .ifPresent(
                existing -> {
                  throw new SecuritiesFirmDuplicateException("이미 등록된 증권사명입니다.");
                });
      }
      firm.setName(newName);
    }

    if (request.getExternalCode() != null) {
      firm.setExternalCode(request.getExternalCode());
    }

    if (request.getBusinessRegistrationNumber() != null) {
      firm.setBusinessRegistrationNumber(request.getBusinessRegistrationNumber());
    }

    firm.setUpdatedAt(LocalDateTime.now(clock));
    SecuritiesFirm saved = securitiesFirmRepository.save(firm);
    return SecuritiesFirmResponse.from(saved);
  }

  /** 증권사를 비활성화(소프트 삭제)한다. */
  public void deactivate(Long id) {
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesFirmNotFoundException("증권사를 찾을 수 없습니다."));

    LocalDateTime now = LocalDateTime.now(clock);
    firm.setStatus(RecordStatus.INACTIVE);
    firm.setDeletedAt(now);
    firm.setUpdatedAt(now);
    securitiesFirmRepository.save(firm);
  }

  private String validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new SecuritiesFirmValidationException("증권사명은 필수입니다.");
    }
    if (name.trim().length() > NAME_MAX_LENGTH) {
      throw new SecuritiesFirmValidationException("증권사명은 최대 255자까지 가능합니다.");
    }
    return name.trim();
  }
}
