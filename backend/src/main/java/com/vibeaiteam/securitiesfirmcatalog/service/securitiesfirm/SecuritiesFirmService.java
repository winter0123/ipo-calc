package com.vibeaiteam.securitiesfirmcatalog.service.securitiesfirm;

import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmRegisterRequest;
import com.vibeaiteam.securitiesfirmcatalog.controller.securitiesfirm.dto.SecuritiesFirmResponse;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmDuplicateException;
import com.vibeaiteam.securitiesfirmcatalog.exception.SecuritiesFirmValidationException;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SecuritiesFirmService {

    private static final int NAME_MAX_LENGTH = 255;
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SecuritiesFirmRepository securitiesFirmRepository;

    public SecuritiesFirmResponse register(SecuritiesFirmRegisterRequest request) {
        String name = validateName(request.getName());

        securitiesFirmRepository.findByNameAndStatus(name, STATUS_ACTIVE).ifPresent(existing -> {
            throw new SecuritiesFirmDuplicateException("이미 등록된 증권사명입니다.");
        });

        LocalDateTime now = LocalDateTime.now();
        SecuritiesFirm securitiesFirm = SecuritiesFirm.builder()
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