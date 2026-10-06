package com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

/**
 * AC 인용: docs/breakdown/broker-readonly-api-pilot.md SF-1. AC-1801: 신규 컬럼이 기존 행에 기본값으로 채워진 채
 * 마이그레이션이 성공한다. AC-1802: 시드를 2회 반복 실행해도 중복 행이 생기지 않는다 (idempotent). AC-1803: 이미 INACTIVE로 동일 이름 행이
 * 있어도 시드가 실패하거나 중복을 만들지 않는다. AC-1804: 한국투자증권만 api_supported=TRUE, 나머지는 FALSE로 시드된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SecuritiesFirmApiColumnsMigrationTest {

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @PersistenceContext private EntityManager entityManager;

  private static final List<String> EXPECTED_NAMES =
      List.of(
          "한국투자증권", "키움증권", "NH투자증권", "미래에셋증권", "삼성증권", "KB증권", "신한투자증권", "하나증권", "대신증권", "유안타증권",
          "한화투자증권", "SK투자증권", "메리츠증권", "토스증권", "신영증권");

  private void executeSeedSql() throws IOException {
    String path = "db/migration/V15__seed_securities_firms.sql";
    ClassPathResource resource = new ClassPathResource(path);
    if (!resource.exists()) {
      throw new IllegalStateException("V15 seed SQL file not found: " + path);
    }
    String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    for (String statement : sql.split(";")) {
      StringBuilder clean = new StringBuilder();
      for (String line : statement.split("\n")) {
        String lineTrimmed = line.trim();
        if (!lineTrimmed.startsWith("--") && !lineTrimmed.isEmpty()) {
          clean.append(lineTrimmed).append(" ");
        }
      }
      String cleanStmt = clean.toString().trim();
      if (!cleanStmt.isEmpty()) {
        entityManager.createNativeQuery(cleanStmt).executeUpdate();
      }
    }
  }

  @Test
  @Transactional
  void migrationAddsNewColumnsWithDefaultsForExistingRows() {
    // Given: 신규 행을 builder로 저장해 스키마 기본값(false/true)이 적용되는지 검증
    SecuritiesFirm newFirm =
        SecuritiesFirm.builder()
            .name("테스트증권")
            .status(RecordStatus.ACTIVE)
            .createdAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .build();
    SecuritiesFirm saved = securitiesFirmRepository.saveAndFlush(newFirm);

    // Then: 신규 행의 api_supported 기본값은 FALSE, api_readonly_only 기본값은 TRUE
    assertThat(saved.getApiSupported()).as("신규 행의 api_supported 기본값은 FALSE").isFalse();
    assertThat(saved.getApiReadonlyOnly()).as("신규 행의 api_readonly_only 기본값은 TRUE").isTrue();
    assertThat(saved.getApiProvider()).isNull();
    assertThat(saved.getApiAuthType()).isNull();
    assertThat(saved.getApiExternalCode()).isNull();
  }

  @Test
  @Transactional
  void seedCreatesExpectedFirmsWithCorrectApiFlags() throws IOException {
    // When: V15 시드 SQL 실행
    executeSeedSql();

    // Then: 15개 이름이 모두 ACTIVE로 존재
    List<SecuritiesFirm> activeFirms = securitiesFirmRepository.findByDeletedAtIsNull();
    List<String> actualNames = activeFirms.stream().map(SecuritiesFirm::getName).toList();
    assertThat(actualNames).as("시드 대상 15개 증권사가 모두 ACTIVE로 존재해야 함").containsAll(EXPECTED_NAMES);

    // And: 한국투자증권만 api_supported=TRUE, api_provider=KIS, 나머지 14개는 FALSE·NULL
    Optional<SecuritiesFirm> kis =
        activeFirms.stream().filter(f -> "한국투자증권".equals(f.getName())).findFirst();
    assertThat(kis).as("한국투자증권 ACTIVE 행이 존재해야 함").isPresent();
    assertThat(kis.get().getApiSupported()).as("한국투자증권만 api_supported=TRUE").isTrue();
    assertThat(kis.get().getApiProvider()).as("한국투자증권의 api_provider=KIS").isEqualTo("KIS");
    assertThat(kis.get().getApiAuthType())
        .as("한국투자증권의 api_auth_type=APP_KEY_SECRET")
        .isEqualTo("APP_KEY_SECRET");
    assertThat(kis.get().getApiReadonlyOnly()).as("한국투자증권의 api_readonly_only=TRUE").isTrue();
    assertThat(kis.get().getApiExternalCode()).as("한국투자증권의 api_external_code=NULL").isNull();

    for (SecuritiesFirm firm : activeFirms) {
      if (!"한국투자증권".equals(firm.getName())) {
        assertThat(firm.getApiSupported()).as("%s는 api_supported=FALSE", firm.getName()).isFalse();
        assertThat(firm.getApiProvider()).as("%s는 api_provider=NULL", firm.getName()).isNull();
        assertThat(firm.getApiAuthType()).as("%s는 api_auth_type=NULL", firm.getName()).isNull();
        assertThat(firm.getApiReadonlyOnly())
            .as("%s는 api_readonly_only=TRUE (기본값)", firm.getName())
            .isTrue();
        assertThat(firm.getApiExternalCode())
            .as("%s는 api_external_code=NULL", firm.getName())
            .isNull();
      }
    }
  }

  @Test
  @Transactional
  void seedIsIdempotentRunningTwiceDoesNotCreateDuplicates() throws IOException {
    // When: 시드 SQL 1회 실행
    executeSeedSql();
    long countAfterFirst = securitiesFirmRepository.count();

    // And: 시드 SQL 2회 실행
    executeSeedSql();
    long countAfterSecond = securitiesFirmRepository.count();

    // Then: 행 수가 증가하지 않음 (AC-1802)
    assertThat(countAfterSecond).as("시드 재실행 시 행 수가 늘어나지 않아야 함 (멱등)").isEqualTo(countAfterFirst);

    // And: 15개 이름이 여전히 ACTIVE 1건씩만 존재
    List<SecuritiesFirm> activeFirms = securitiesFirmRepository.findByDeletedAtIsNull();
    for (String name : EXPECTED_NAMES) {
      long nameCount = activeFirms.stream().filter(f -> name.equals(f.getName())).count();
      assertThat(nameCount).as("%s ACTIVE 행은 1건만 존재", name).isEqualTo(1);
    }
  }

  @Test
  @Transactional
  void seedHandlesExistingInactiveRowWithoutFailureOrDuplicate() throws IOException {
    // Given: 시드 이름 중 하나(메리츠증권)의 기존 ACTIVE 행을 삭제하고 동일 이름 INACTIVE 행 삽입
    String testName = "메리츠증권";
    securitiesFirmRepository.deleteAll(
        securitiesFirmRepository.findAll().stream()
            .filter(f -> testName.equals(f.getName()) && f.getStatus() == RecordStatus.ACTIVE)
            .toList());
    SecuritiesFirm inactiveFirm =
        SecuritiesFirm.builder()
            .name(testName)
            .status(RecordStatus.INACTIVE)
            .deletedAt(java.time.LocalDateTime.now())
            .createdAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .apiSupported(false)
            .apiReadonlyOnly(true)
            .build();
    securitiesFirmRepository.saveAndFlush(inactiveFirm);

    // When: 시드 SQL 실행 (INACTIVE 행이 있어도 ACTIVE 행 생성이 허용되어야 함)
    executeSeedSql();

    // Then: 실패하지 않고 ACTIVE 행 1건 생성됨 (AC-1803)
    // And: 이름 기준(메리츠증권)으로 ACTIVE 1건 + INACTIVE 1건 = 총 2건
    List<SecuritiesFirm> firms =
        securitiesFirmRepository.findAll().stream()
            .filter(f -> testName.equals(f.getName()))
            .toList();
    assertThat(firms).as("INACTIVE 1건 + ACTIVE 1건 = 총 2건").hasSize(2);

    long activeCount = firms.stream().filter(f -> f.getStatus() == RecordStatus.ACTIVE).count();
    long inactiveCount = firms.stream().filter(f -> f.getStatus() == RecordStatus.INACTIVE).count();
    assertThat(activeCount).as("ACTIVE 행은 1건").isEqualTo(1);
    assertThat(inactiveCount).as("INACTIVE 행은 1건").isEqualTo(1);

    // And: 생성된 ACTIVE 행의 api_supported가 FALSE (메리츠증권은 시드에서 FALSE)
    SecuritiesFirm activeFirm =
        firms.stream().filter(f -> f.getStatus() == RecordStatus.ACTIVE).findFirst().orElseThrow();
    assertThat(activeFirm.getApiSupported())
        .as("시드로 생성된 메리츠증권 ACTIVE 행은 api_supported=FALSE")
        .isFalse();
    assertThat(activeFirm.getApiProvider())
        .as("시드로 생성된 메리츠증권 ACTIVE 행은 api_provider=NULL")
        .isNull();
  }
}
