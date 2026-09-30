package com.vibeaiteam.ipocatalog.repository.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import java.time.LocalDate;
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
 * AC 인용: docs/spec/ipo-catalog.md, docs/work-log/ipo-catalog.md - AC-001: IPO 등록 성공 — 신규 Ipo 생성 시
 * status=ACTIVE, deleted_at=null - D-IPO-001: DB partial unique index로 활성 IPO 간 중복 방지 (stock_code /
 * ipo_name+subscription_start_date) - D-IPO-004: 목록/상세 조회는 deletedAt IS NULL(=존재하는 레코드)만 노출.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoRepositoryTest {

  @Autowired private IpoRepository ipoRepository;

  @Test
  void saveIpoFindByIdReturnsIpoWithActiveStatus() {
    // Given: 저장할 IPO가 존재함 (필수 필드만 설정, status 기본값 ACTIVE)
    // - spec AC-001: 등록 시 status=ACTIVE, deleted_at=null로 생성된다

    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        Ipo.builder()
            .companyName("테스트기업")
            .ipoName("테스트공모주")
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    // When: IPO 저장 후 findByIdAndDeletedAtIsNull로 조회
    Ipo saved = ipoRepository.save(ipo);
    Optional<Ipo> found = ipoRepository.findByIdAndDeletedAtIsNull(saved.getId());

    // Then: 저장된 IPO가 id, companyName, ipoName, status로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getCompanyName()).isEqualTo("테스트기업");
    assertThat(found.get().getIpoName()).isEqualTo("테스트공모주");
    assertThat(found.get().getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(found.get().getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 10, 2));
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findAllByDeletedAtIsNullOrderByCreatedAtDescReturnsOnlyActiveIpos() {
    // Given: ACTIVE IPO 2곳과 논리 삭제된 IPO 1곳이 저장되어 있음
    // - D-IPO-004: 목록 조회는 deletedAt IS NULL(=존재하는 레코드)만 노출

    LocalDateTime now = LocalDateTime.now();
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업A")
            .ipoName("공모주A")
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업B")
            .ipoName("공모주B")
            .subscriptionStartDate(LocalDate.of(2026, 11, 1))
            .subscriptionEndDate(LocalDate.of(2026, 11, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now.plusDays(1))
            .updatedAt(now.plusDays(1))
            .build());
    ipoRepository.save(
        Ipo.builder()
            .companyName("폐지기업")
            .ipoName("폐지공모주")
            .subscriptionStartDate(LocalDate.of(2026, 9, 1))
            .subscriptionEndDate(LocalDate.of(2026, 9, 2))
            .status(RecordStatus.INACTIVE)
            .deletedAt(now)
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build());

    // When: findAllByDeletedAtIsNullOrderByCreatedAtDesc 호출
    List<Ipo> result = ipoRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();

    // Then: 논리 삭제된 IPO가 제외되고 createdAt 내림차순으로 정렬된 목록이 반환된다
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getCompanyName()).isEqualTo("기업B"); // createdAt이 더 나중
    assertThat(result.get(1).getCompanyName()).isEqualTo("기업A");
  }

  @Test
  void findByIdAndDeletedAtIsNullReturnsOnlyActiveIpo() {
    // Given: ACTIVE IPO와 논리 삭제된 IPO가 저장되어 있음
    // - D-IPO-004: 상세 조회는 deletedAt IS NULL(=존재하는 레코드)만 노출

    LocalDateTime now = LocalDateTime.now();
    Ipo active =
        ipoRepository.save(
            Ipo.builder()
                .companyName("활성기업")
                .ipoName("활성공모주")
                .subscriptionStartDate(LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(LocalDate.of(2026, 10, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    Ipo deleted =
        ipoRepository.save(
            Ipo.builder()
                .companyName("삭제기업")
                .ipoName("삭제공모주")
                .subscriptionStartDate(LocalDate.of(2026, 9, 1))
                .subscriptionEndDate(LocalDate.of(2026, 9, 2))
                .status(RecordStatus.INACTIVE)
                .deletedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<Ipo> activeResult = ipoRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<Ipo> deletedResult = ipoRepository.findByIdAndDeletedAtIsNull(deleted.getId());

    // Then: ACTIVE IPO만 반환되고 논리 삭제된 IPO는 반환되지 않는다
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getCompanyName()).isEqualTo("활성기업");
    assertThat(deletedResult).isEmpty();
  }

  @Test
  void saveDuplicateActiveStockCodeThrowsDataIntegrityViolationException() {
    // Given: 동일 stock_code의 ACTIVE IPO가 하나 저장되어 있음
    // - D-IPO-001: uk_ipo_stock_code_active 부분 유니크 인덱스로 활성 IPO 간 stock_code 중복 방지
    // - spec AC-005: 중복 stock_code 등록은 409 Conflict (DB 제약상 DataIntegrityViolation 반환)

    LocalDateTime now = LocalDateTime.now();
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업A")
            .ipoName("공모주A")
            .stockCode("123456")
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    Ipo duplicate =
        Ipo.builder()
            .companyName("기업B")
            .ipoName("공모주B")
            .stockCode("123456") // 동일 stock_code
            .subscriptionStartDate(LocalDate.of(2026, 11, 1))
            .subscriptionEndDate(LocalDate.of(2026, 11, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When & Then: 동일 stock_code + ACTIVE로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class, () -> ipoRepository.saveAndFlush(duplicate));
  }

  @Test
  void saveDuplicateActiveIpoNameAndStartDateThrowsDataIntegrityViolationException() {
    // Given: 동일 ipo_name + subscription_start_date 조합의 ACTIVE IPO가 하나 저장되어 있음
    // - D-IPO-001: uk_ipo_name_start_date_active 부분 유니크 인덱스로 활성 IPO 간 조합 중복 방지
    // - spec AC-005: 중복 (ipoName, subscriptionStartDate) 등록은 409 Conflict

    LocalDateTime now = LocalDateTime.now();
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업A")
            .ipoName("공모주A")
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    Ipo duplicate =
        Ipo.builder()
            .companyName("기업B")
            .ipoName("공모주A") // 동일 ipo_name
            .subscriptionStartDate(LocalDate.of(2026, 10, 1)) // 동일 subscription_start_date
            .subscriptionEndDate(LocalDate.of(2026, 11, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When & Then: 동일 (ipo_name, subscription_start_date) + ACTIVE로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class, () -> ipoRepository.saveAndFlush(duplicate));
  }

  @Test
  void saveNullStockCodeAllowedForMultipleActiveIpos() {
    // Given: stock_code가 null인 ACTIVE IPO가 저장되어 있음
    // - D-IPO-001: stock_code가 NULL인 경우 uk_ipo_stock_code_active 제약에서 제외됨
    // - spec 엣지케이스: stock_code가 null이면 중복 검사에서 제외하고 ipo_name+startDate 조합으로 검사

    LocalDateTime now = LocalDateTime.now();
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업A")
            .ipoName("공모주A")
            .stockCode(null)
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: stock_code가 null인 또 다른 ACTIVE IPO 저장
    Ipo another =
        ipoRepository.saveAndFlush(
            Ipo.builder()
                .companyName("기업B")
                .ipoName("공모주B")
                .stockCode(null) // stock_code null 허용
                .subscriptionStartDate(LocalDate.of(2026, 11, 1))
                .subscriptionEndDate(LocalDate.of(2026, 11, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    // Then: 유니크 위반 없이 저장된다 (stock_code NULL은 부분 유니크 제약에서 제외)
    assertThat(another.getId()).isNotNull();
    assertThat(another.getStockCode()).isNull();
  }

  @Test
  void saveSameStockCodeAfterSoftDeleteAllowed() {
    // Given: "123456" stock_code를 가진 ACTIVE IPO가 저장 후 논리 삭제(status=INACTIVE, deletedAt)됨
    // - spec: 삭제된 IPO(deletedAt 설정)는 조회 제외, 동일 stock_code 재등록 허용
    // - D-IPO-001: INACTIVE 레코드는 부분 유니크 제약에 영향받지 않음 (재등록 가능)

    LocalDateTime now = LocalDateTime.now();
    Ipo existing =
        ipoRepository.save(
            Ipo.builder()
                .companyName("기존기업")
                .ipoName("기존공모주")
                .stockCode("123456")
                .subscriptionStartDate(LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(LocalDate.of(2026, 10, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    existing.setStatus(RecordStatus.INACTIVE);
    existing.setDeletedAt(now);
    ipoRepository.saveAndFlush(existing);

    // When: 동일 stock_code로 새 ACTIVE IPO 저장
    Ipo reRegistered =
        ipoRepository.saveAndFlush(
            Ipo.builder()
                .companyName("신규기업")
                .ipoName("신규공모주")
                .stockCode("123456")
                .subscriptionStartDate(LocalDate.of(2026, 11, 1))
                .subscriptionEndDate(LocalDate.of(2026, 11, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    // Then: 유니크 위반 없이 저장된다 (활성 IPO 간 중복만 제약)
    assertThat(reRegistered.getId()).isNotNull();
    assertThat(reRegistered.getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  @Test
  void saveAndFindByCorpCodeReturnsIpo() {
    // Given: corp_code가 있는 ACTIVE IPO 저장
    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        Ipo.builder()
            .companyName("테스트기업")
            .ipoName("테스트공모주")
            .corpCode("12345678")
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    // When: 저장 후 findByCorpCodeAndStatusAndDeletedAtIsNull로 조회
    Ipo saved = ipoRepository.saveAndFlush(ipo);
    Optional<Ipo> found =
        ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("12345678", RecordStatus.ACTIVE);

    // Then: 저장된 IPO가 corp_code, companyName, ipoName으로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getCorpCode()).isEqualTo("12345678");
    assertThat(found.get().getCompanyName()).isEqualTo("테스트기업");
    assertThat(found.get().getIpoName()).isEqualTo("테스트공모주");
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
  }

  @Test
  void saveDuplicateActiveCorpCodeThrowsDataIntegrityViolationException() {
    // Given: 동일 corp_code의 ACTIVE IPO가 하나 저장되어 있음
    // - uk_ipo_corp_code_active 부분 유니크 인덱스로 활성 IPO 간 corp_code 중복 방지

    LocalDateTime now = LocalDateTime.now();
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업A")
            .ipoName("공모주A")
            .corpCode("12345678")
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    Ipo duplicate =
        Ipo.builder()
            .companyName("기업B")
            .ipoName("공모주B")
            .corpCode("12345678") // 동일 corp_code
            .subscriptionStartDate(LocalDate.of(2026, 11, 1))
            .subscriptionEndDate(LocalDate.of(2026, 11, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    // When & Then: 동일 corp_code + ACTIVE로 저장 시 데이터 무결성 위반 예외 발생
    assertThrows(
        DataIntegrityViolationException.class, () -> ipoRepository.saveAndFlush(duplicate));
  }

  @Test
  void saveNullCorpCodeAllowedForMultipleActiveIpos() {
    // Given: corp_code가 null인 ACTIVE IPO가 저장되어 있음
    // - corp_code가 NULL인 경우 uk_ipo_corp_code_active 제약에서 제외됨

    LocalDateTime now = LocalDateTime.now();
    ipoRepository.save(
        Ipo.builder()
            .companyName("기업A")
            .ipoName("공모주A")
            .corpCode(null)
            .subscriptionStartDate(LocalDate.of(2026, 10, 1))
            .subscriptionEndDate(LocalDate.of(2026, 10, 2))
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build());

    // When: corp_code가 null인 또 다른 ACTIVE IPO 저장
    Ipo another =
        ipoRepository.saveAndFlush(
            Ipo.builder()
                .companyName("기업B")
                .ipoName("공모주B")
                .corpCode(null) // corp_code null 허용
                .subscriptionStartDate(LocalDate.of(2026, 11, 1))
                .subscriptionEndDate(LocalDate.of(2026, 11, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    // Then: 유니크 위반 없이 저장된다 (corp_code NULL은 부분 유니크 제약에서 제외)
    assertThat(another.getId()).isNotNull();
    assertThat(another.getCorpCode()).isNull();
  }

  @Test
  void saveSameCorpCodeAfterSoftDeleteAllowed() {
    // Given: "12345678" corp_code를 가진 ACTIVE IPO가 저장 후 논리 삭제(status=INACTIVE, deletedAt)됨
    // - 삭제된 IPO(deletedAt 설정)는 조회 제외, 동일 corp_code 재등록 허용
    // - INACTIVE 레코드는 부분 유니크 제약에 영향받지 않음 (재등록 가능)

    LocalDateTime now = LocalDateTime.now();
    Ipo existing =
        ipoRepository.save(
            Ipo.builder()
                .companyName("기존기업")
                .ipoName("기존공모주")
                .corpCode("12345678")
                .subscriptionStartDate(LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(LocalDate.of(2026, 10, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    existing.setStatus(RecordStatus.INACTIVE);
    existing.setDeletedAt(now);
    ipoRepository.saveAndFlush(existing);

    // When: 동일 corp_code로 새 ACTIVE IPO 저장
    Ipo reRegistered =
        ipoRepository.saveAndFlush(
            Ipo.builder()
                .companyName("신규기업")
                .ipoName("신규공모주")
                .corpCode("12345678")
                .subscriptionStartDate(LocalDate.of(2026, 11, 1))
                .subscriptionEndDate(LocalDate.of(2026, 11, 2))
                .status(RecordStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    // Then: 유니크 위반 없이 저장된다 (활성 IPO 간 중복만 제약)
    assertThat(reRegistered.getId()).isNotNull();
    assertThat(reRegistered.getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }
}
