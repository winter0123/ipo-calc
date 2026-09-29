package com.vibeaiteam.securitiesfirmcatalog.service.securitiesfirm;

import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmRegisterRequest;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmResponse;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmUpdateRequest;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmDuplicateException;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmNotFoundException;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmValidationException;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecuritiesFirmService {

  private static final int NAME_MAX_LENGTH = 255;
  private static final String STATUS_ACTIVE = "ACTIVE";
  private static final String STATUS_INACTIVE = "INACTIVE";

  private final SecuritiesFirmRepository securitiesFirmRepository;

  public SecuritiesFirmResponse register(SecuritiesFirmRegisterRequest request) {
    String name = validateName(request.getName());

    securitiesFirmRepository
        .findByNameAndStatus(name, STATUS_ACTIVE)
        .ifPresent(
            existing -> {
              throw new SecuritiesFirmDuplicateException("이미 등록된 증권사명입니다.");
            });

    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm securitiesFirm =
        SecuritiesFirm.builder()
            .name(name)
            .externalCode(request.getExternalCode())
            .businessRegistrationNumber(request.getBusinessRegistrationNumber())
            .status(STATUS_ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    SecuritiesFirm saved = securitiesFirmRepository.save(securitiesFirm);
    return SecuritiesFirmResponse.from(saved);
  }

  public List<SecuritiesFirmResponse> list() {
    List<SecuritiesFirm> firms = securitiesFirmRepository.findByDeletedAtIsNull();
    return firms.stream().map(SecuritiesFirmResponse::from).toList();
  }

  public SecuritiesFirmResponse getById(Long id) {
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesFirmNotFoundException("증권사를 찾을 수 없습니다."));
    return SecuritiesFirmResponse.from(firm);
  }

  public SecuritiesFirmResponse update(Long id, SecuritiesFirmUpdateRequest request) {
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesFirmNotFoundException("증권사를 찾을 수 없습니다."));

    if (request.getName() != null) {
      String newName = validateName(request.getName());
      if (!newName.equals(firm.getName())) {
        securitiesFirmRepository
            .findByNameAndStatus(newName, STATUS_ACTIVE)
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

    firm.setUpdatedAt(LocalDateTime.now());
    SecuritiesFirm saved = securitiesFirmRepository.save(firm);
    return SecuritiesFirmResponse.from(saved);
  }

  public void deactivate(Long id) {
    SecuritiesFirm firm =
        securitiesFirmRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SecuritiesFirmNotFoundException("증권사를 찾을 수 없습니다."));

    LocalDateTime now = LocalDateTime.now();
    firm.setStatus(STATUS_INACTIVE);
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
