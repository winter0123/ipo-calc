package com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/spec/securities-firm-catalog.md, docs/work-log/securities-firm-catalog.md - AC-001:
 * 증권사 등록 성공 — 신규 SecuritiesFirm 생성 시 status=ACTIVE, deleted_at=null - D-SF-001: (name, status) 복합
 * 유니크 제약으로 활성 증권사 간 이름 중복만 방지 - D-SF-003: 목록/상세 조회는 deletedAt IS NULL(=ACTIVE)만 노출.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesFirmRepositoryTest {

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  private String firmNameSuffix;

  @BeforeEach
  void setUp() {
    firmNameSuffix = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
  }

  /**
   * 시드(V15)의 14개 증권사와 절대 겹치지 않는 고유 증권사명을 만든다.
   *
   * <p>시드 이름(예: 한국투자증권)을 그대로 쓰면 (name, status) 유니크 제약 때문에 저장이 실패하고, 목록 조회의 절대 개수도 시드 때문에
   * 어긋난다. @DataJpaTest의 롤백으로 행은 남지 않지만, 이름은 테스트마다 달라야 시드와 충돌하지 않는다.
   */
  private String uniqueFirmName(String base) {
    return base + "-" + firmNameSuffix;
  }

  @Test
  void saveSecuritiesFirmFindByIdReturnsFirmWithActiveStatus() {
    // Given: 저장할 증권사(name, status 기본값 ACTIVE)가 존재함
    // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null로 생성된다

    String firmName = uniqueFirmName("한국투자증권");
    SecuritiesFirm securitiesFirm =
        SecuritiesFirm.builder()
            .name(firmName)
            .externalCode("SC-0001")
            .businessRegistrationNumber("123-45-67890")
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When: 증권사 저장 후 findById로 조회
    SecuritiesFirm saved = securitiesFirmRepository.save(securitiesFirm);
    Optional<SecuritiesFirm> found = securitiesFirmRepository.findById(saved.getId());

    // Then: 저장된 증권사가 id, name, external_code, status로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getName()).isEqualTo(firmName);
    assertThat(found.get().getExternalCode()).isEqualTo("SC-0001");
    assertThat(found.get().getBusinessRegistrationNumber()).isEqualTo("123-45-67890");
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findByNameAndStatusReturnsMatchingFirm() {
    // Given: ACTIVE 상태의 증권사가 저장되어 있음
    // - D-SF-001: 활성 증권사 간 이름 중복 방지 검증용 조회 메서드

    String firmName = uniqueFirmName("미래에셋증권");
    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name(firmName)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: 동일 이름 + ACTIVE 상태로 findByNameAndStatus 호출
    Optional<SecuritiesFirm> result =
        securitiesFirmRepository.findByNameAndStatus(firmName, RecordStatus.ACTIVE);

    // Then: 해당 증권사가 반환된다
    assertThat(result).isPresent();
    assertThat(result.get().getName()).isEqualTo(firmName);
    assertThat(result.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void findByNameAndStatusReturnsEmptyWhenNoActiveMatch() {
    // Given: INACTIVE 상태의 증권사만 저장되어 있음

    String firmName = uniqueFirmName("옛증권");
    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name(firmName)
            .status(RecordStatus.INACTIVE)
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: 동일 이름 + ACTIVE 상태로 findByNameAndStatus 호출
    Optional<SecuritiesFirm> result =
        securitiesFirmRepository.findByNameAndStatus(firmName, RecordStatus.ACTIVE);

    // Then: 빈 Optional이 반환된다 (INACTIVE는 활성 중복 대상이 아님)
    assertThat(result).isEmpty();
  }

  @Test
  void findByDeletedAtIsNullExcludesSoftDeletedFirms() {
    // Given: ACTIVE 증권사 2곳과 deleteAt이 설정된(논리 삭제) 증권사 1곳이 저장되어 있음
    // - D-SF-003: 목록 조회는 deletedAt IS NULL(=ACTIVE)만 노출
    // 시드(V15)의 증권사도 함께 조회되므로 절대 개수 대신 "내가 만든 이름의 포함/제외"로 검증한다.

    String firstActiveName = uniqueFirmName("삼성증권");
    String secondActiveName = uniqueFirmName("NH투자증권");
    String deletedName = uniqueFirmName("폐지증권");
    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name(firstActiveName)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name(secondActiveName)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name(deletedName)
            .status(RecordStatus.INACTIVE)
            .deletedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
    // findByDeletedAtIsNull이 빠뜨리면 안 되는 기준: 전체 행 중 논리 삭제되지 않은 행
    int expectedActiveCount =
        (int)
            securitiesFirmRepository.findAll().stream()
                .filter(firm -> firm.getDeletedAt() == null)
                .count();

    // When: findByDeletedAtIsNull 호출
    List<SecuritiesFirm> result = securitiesFirmRepository.findByDeletedAtIsNull();

    // Then: 논리 삭제된 증권사가 제외된 목록이 반환된다
    assertThat(result)
        .as("저장한 활성 증권사는 모두 반환되어야 합니다 (D-SF-003)")
        .extracting(SecuritiesFirm::getName)
        .contains(firstActiveName, secondActiveName);
    assertThat(result)
        .as("논리 삭제된 증권사는 제외되어야 합니다 (D-SF-003)")
        .extracting(SecuritiesFirm::getName)
        .doesNotContain(deletedName);
    assertThat(result)
        .as("반환된 모든 증권사는 논리 삭제되지 않은 활성 증권사여야 합니다 (D-SF-003)")
        .allSatisfy(firm -> assertThat(firm.getDeletedAt()).isNull());
    assertThat(result)
        .as("전체 활성 증권사(시드 포함)만 빠짐없이 반환되어야 합니다 (D-SF-003)")
        .hasSize(expectedActiveCount);
  }

  @Test
  void findByIdAndDeletedAtIsNullReturnsOnlyActiveFirm() {
    // Given: ACTIVE 증권사와 논리 삭제된 증권사가 저장되어 있음
    // - D-SF-003: 상세 조회는 deletedAt IS NULL(=ACTIVE)만 노출

    String activeName = uniqueFirmName("KB증권");
    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm active =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name(activeName)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    SecuritiesFirm deleted =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name(uniqueFirmName("OLD증권"))
                .status(RecordStatus.INACTIVE)
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
    assertThat(activeResult.get().getName()).isEqualTo(activeName);
    assertThat(deletedResult).isEmpty();
  }

  @Test
  void saveDuplicateActiveNameThrowsDataIntegrityViolationException() {
    // Given: 동일 이름의 ACTIVE 증권사가 하나 저장되어 있음
    // - D-SF-001: (name, status) 복합 유니크 제약으로 활성 증권사 간 중복 방지
    // - spec AC-003: 중복 이름 등록은 409 Conflict (DB 제약상 DataIntegrityViolation 반환)

    String firmName = uniqueFirmName("한국투자증권");
    LocalDateTime now = LocalDateTime.now();
    securitiesFirmRepository.save(
        SecuritiesFirm.builder()
            .name(firmName)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    SecuritiesFirm duplicate =
        SecuritiesFirm.builder()
            .name(firmName)
            .status(RecordStatus.ACTIVE)
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
    // Given: ACTIVE 증권사가 저장 후 논리 삭제(status=INACTIVE, deletedAt)됨
    // - spec: 삭제된 증권사(deletedAt 설정)는 조회 제외, 동일 이름 재등록 허용
    // - D-SF-001: INACTIVE 레코드는 유니크 제약에 영향받지 않음 (재등록 가능)

    String firmName = uniqueFirmName("미래에셋증권");
    LocalDateTime now = LocalDateTime.now();
    SecuritiesFirm existing =
        securitiesFirmRepository.save(
            SecuritiesFirm.builder()
                .name(firmName)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    existing.setStatus(RecordStatus.INACTIVE);
    existing.setDeletedAt(now);
    securitiesFirmRepository.saveAndFlush(existing);

    // When: 동일 이름으로 새 ACTIVE 증권사 저장
    SecuritiesFirm reRegistered =
        securitiesFirmRepository.saveAndFlush(
            SecuritiesFirm.builder()
                .name(firmName)
                .status(RecordStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    // Then: 유니크 위반 없이 저장된다 (활성 증권사 간 중복만 제약)
    assertThat(reRegistered.getId()).isNotNull();
    assertThat(reRegistered.getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }
}
