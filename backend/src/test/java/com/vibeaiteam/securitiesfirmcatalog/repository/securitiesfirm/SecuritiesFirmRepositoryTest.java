package com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/spec/securities-firm-catalog.md, docs/work-log/securities-firm-catalog.md - AC-001:
 * 증권사 등록 성공 — 신규 SecuritiesFirm 생성 시 status=ACTIVE, deleted_at=null - D-SF-001: (name, status) 복합
 * 유니크 제약으로 활성 증권사 간 이름 중복만 방지 - D-SF-003: 목록/상세 조회는 deletedAt IS NULL(=ACTIVE)만 노출
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesFirmRepositoryTest {

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Test
  void saveSecuritiesFirmFindByIdReturnsFirmWithActiveStatus() {
    // Given: 저장할 증권사(name, status 기본값 ACTIVE)가 존재함
    // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null로 생성된다

    SecuritiesFirm securitiesFirm =
        SecuritiesFirm.builder()
            .name("한국투자증권")
            .externalCode("SC-0001")
            .businessRegistrationNumber("123-45-67890")
            .status("ACTIVE")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When: 증권사 저장 후 findById로 조회
    SecuritiesFirm saved = securitiesFirmRepository.save(securitiesFirm);
    Optional<SecuritiesFirm> found = securitiesFirmRepository.findById(saved.getId());

    // Then: 저장된 증권사가 id, name, external_code, status로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getName()).isEqualTo("한국투자증권");
    assertThat(found.get().getExternalCode()).isEqualTo("SC-0001");
    assertThat(found.get().getBusinessRegistrationNumber()).isEqualTo("123-45-67890");
    assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findByNameAndStatusReturnsMatchingFirm() {
    // Given: ACTIVE 상태의 증권사가 저장되어 있음
    // - D-SF-001: 활성 증권사 간 이름 중복 방지 검증용 조회 메서드

    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name("미래에셋증권")
            .status("ACTIVE")
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: 동일 이름 + ACTIVE 상태로 findByNameAndStatus 호출
    Optional<SecuritiesFirm> result =
        securitiesFirmRepository.findByNameAndStatus("미래에셋증권", "ACTIVE");

    // Then: 해당 증권사가 반환된다
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo("미래에셋증권");
    assertThat(result.get().getStatus()).isEqualTo("ACTIVE");
  }

  @Test
  void findByNameAndStatusReturnsEmptyWhenNoActiveMatch() {
    // Given: INACTIVE 상태의 증권사만 저장되어 있음

    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name("옛증권")
            .status("INACTIVE")
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: 동일 이름 + ACTIVE 상태로 findByNameAndStatus 호출
    Optional<SecuritiesFirm> result = securitiesFirmRepository.findByNameAndStatus("옛증권", "ACTIVE");

    // Then: 빈 Optional이 반환된다 (INACTIVE는 활성 중복 대상이 아님)
    assertThat(result).isEmpty();
  }

  @Test
  void findByDeletedAtIsNullExcludesSoftDeletedFirms() {
    // Given: ACTIVE 증권사 2곳과 deleteAt이 설정된(논리 삭제) 증권사 1곳이 저장되어 있음
    // - D-SF-003: 목록 조회는 deletedAt IS NULL(=ACTIVE)만 노출

    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name("삼성증권")
            .status("ACTIVE")
            .createdAt(now)
            .updatedAt(now)
            .build());
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name("NH투자증권")
            .status("ACTIVE")
            .createdAt(now)
            .updatedAt(now)
            .build());
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name("폐지증권")
            .status("INACTIVE")
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: findByDeletedAtIsNull 호출
    List<SecuritiesFirm> result = securitiesFirmRepository.findByDeletedAtIsNull();

    // Then: 논리 삭제된 증권사가 제외된 목록이 반환된다
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(SecuritiesFirm::getName)
        .containsExactlyInAnyOrder("삼성증권", "NH투자증권");
  }

  @Test
  void findByIdAndDeletedAtIsNullReturnsOnlyActiveFirm() {
    // Given: ACTIVE 증권사와 논리 삭제된 증권사가 저장되어 있음
    // - D-SF-003: 상세 조회는 deletedAt IS NULL(=ACTIVE)만 노출

    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm active =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("KB증권")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
    SecuritiesFirm deleted =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("OLD증권")
                .status("INACTIVE")
                .deletedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<SecuritiesFirm> activeResult =
        securitiesFirmRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<SecuritiesFirm> deletedResult =
        securitiesFirmRepository.findByIdAndDeletedAtIsNull(deleted.getId());

    // Then: ACTIVE 증권사만 반환되고 논리 삭제된 증권사는 반환되지 않는다
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getName()).isEqualTo("KB증권");
    assertThat(deletedResult).isEmpty();
  }

  @Test
  void saveDuplicateActiveNameThrowsDataIntegrityViolationException() {
    // Given: 동일 이름의 ACTIVE 증권사가 하나 저장되어 있음
    // - D-SF-001: (name, status) 복합 유니크 제약으로 활성 증권사 간 중복 방지
    // - spec AC-003: 중복 이름 등록은 409 Conflict (DB 제약상 DataIntegrityViolation 반환)

    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name("한국투자증권")
            .status("ACTIVE")
            .createdAt(now)
            .updatedAt(now)
            .build());

    SecuritiesFirm duplicate =
        SecuritiesFirm.builder()
            .name("한국투자증권")
            .status("ACTIVE")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When & Then: 동일 (name, status=ACTIVE)로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class,
        () -> securitiesFirmRepository.saveAndFlush(duplicate));
  }

  @Test
  void saveSameNameAfterSoftDeleteAllowed() {
    // Given: "미래에셋증권" ACTIVE 증권사가 저장 후 논리 삭제(status=INACTIVE, deletedAt)됨
    // - spec: 삭제된 증권사(deletedAt 설정)는 조회 제외, 동일 이름 재등록 허용
    // - D-SF-001: INACTIVE 레코드는 유니크 제약에 영향받지 않음 (재등록 가능)

    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm existing =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name("미래에셋증권")
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build());
    existing.setStatus("INACTIVE");
    existing.setDeletedAt(now);
    securitiesFirmRepository.saveAndFlush(existing);

    // When: 동일 이름으로 새 ACTIVE 증권사 저장
    SecuritiesFirm reRegistered =
        securitiesFirmRepository.saveAndFlush(
            SecuritiesFirm.builder()
                .name("미래에셋증권")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    // Then: 유니크 위반 없이 저장된다 (활성 증권사 간 중복만 제약)
    assertThat(reRegistered.getId()).isNotNull();
    assertThat(reRegistered.getStatus()).isEqualTo("ACTIVE");
  }
}
