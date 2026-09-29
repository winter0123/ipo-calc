package com.vibeaiteam.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link IpoSyncService} 단위 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoSyncServiceTest {

  @MockBean private OpenDartCorpDisclosureClient corpDisclosureClient;

  @MockBean private OpenDartEquitySecuritiesClient equitySecuritiesClient;

  @MockBean private IpoRepository ipoRepository;

  @MockBean private BusinessDayCalculator businessDayCalculator;

  // TransactionTemplate은 실제 빈 사용 (Mock으로 람다 인자 캡처 시 타입 문제 발생)
  @Autowired private TransactionTemplate transactionTemplate;

  private IpoSyncService syncService;

  @Captor private ArgumentCaptor<Ipo> ipoCaptor;

  @BeforeEach
  void setUp() {
    syncService =
        new IpoSyncService(
            corpDisclosureClient,
            equitySecuritiesClient,
            ipoRepository,
            businessDayCalculator,
            transactionTemplate);
  }

  private OpenDartDisclosureItem createCompany(String corpCode, String corpName, String stockCode) {
    return OpenDartDisclosureItem.builder()
        .corpCode(corpCode)
        .corpName(corpName)
        .stockCode(stockCode)
        .reportNm("증권신고서(지분증권)")
        .build();
  }

  private OpenDartEquitySecurityDetail createDetail(
      String corpCode, String corpName, String sbd, String stkcnt, String slprc) {
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode(corpCode)
            .corpName(corpName)
            .sbd(sbd)
            .stkcnt(stkcnt)
            .slprc(slprc)
            .build();
    detail.parseDerivedFields();
    return detail;
  }

  @Test
  void sync_createsNewIpo_whenNoExistingRecord() {
    // given
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "10,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01658035", "ACTIVE"))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "옵토닉스", LocalDate.of(2026, 11, 5), "ACTIVE"))
        .thenReturn(Optional.empty());

    when(businessDayCalculator.nextBusinessDay(LocalDate.of(2026, 11, 5)))
        .thenReturn(LocalDate.of(2026, 11, 6));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(1);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getSkippedManual()).isEqualTo(0);
    assertThat(result.getSkippedNonIpo()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(0);
    assertThat(result.isAborted()).isFalse();

    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getCorpCode()).isEqualTo("01658035");
    assertThat(saved.getCompanyName()).isEqualTo("옵토닉스");
    assertThat(saved.getIpoName()).isEqualTo("옵토닉스");
    assertThat(saved.getStockCode()).isNull();
    assertThat(saved.getLowerBandPrice()).isEqualTo(10_000L);
    assertThat(saved.getUpperBandPrice()).isEqualTo(10_000L);
    assertThat(saved.getFixedPrice()).isNull(); // 동기화가 절대 설정하지 않음
    assertThat(saved.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(saved.getOfferingShareCount()).isEqualTo(2_200_000L);
    assertThat(saved.getSyncSource()).isEqualTo("OPENDART");
    assertThat(saved.getLastSyncedAt()).isNotNull();
    assertThat(saved.getStatus()).isEqualTo("ACTIVE");
  }

  @Test
  void sync_singleDateSbd_usesNextBusinessDayForEndDate() {
    // given: 단일 날짜 sbd → 종료일은 다음 영업일
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일", "1,000,000", "15,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01658035", "ACTIVE"))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "옵토닉스", LocalDate.of(2026, 11, 5), "ACTIVE"))
        .thenReturn(Optional.empty());

    when(businessDayCalculator.nextBusinessDay(LocalDate.of(2026, 11, 5)))
        .thenReturn(LocalDate.of(2026, 11, 6)); // 금요일 다음은 월요일 (주말 제외)

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(saved.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6)); // 다음 영업일
    assertThat(saved.getFixedPrice()).isNull();
    assertThat(saved.getListingDate()).isNull();
  }

  @Test
  void sync_updatesExistingAutoSyncRecord_preservesFixedPriceAndListingDate() {
    // given: 기존 자동 동기화 레코드 존재 (syncSource != null)
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "3,000,000", "12,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    // 기존 레코드: 수동 입력으로 fixedPrice=11000, listingDate 설정됨
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .companyName("옵토닉스")
            .ipoName("옵토닉스")
            .corpCode("01658035")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(11_000L) // 수동 입력값 (보존되어야 함)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .listingDate(LocalDate.of(2026, 11, 20)) // 수동 입력값 (보존되어야 함)
            .offeringShareCount(2_200_000L)
            .status("ACTIVE")
            .syncSource("OPENDART")
            .lastSyncedAt(LocalDateTime.now().minusDays(1))
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now().minusDays(1))
            .build();

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01658035", "ACTIVE"))
        .thenReturn(Optional.of(existing));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(1);
    assertThat(result.getSkippedManual()).isEqualTo(0);

    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    // 가격 밴드 갱신됨
    assertThat(saved.getLowerBandPrice()).isEqualTo(12_000L);
    assertThat(saved.getUpperBandPrice()).isEqualTo(12_000L);
    // fixedPrice 보존됨 (동기화가 덮어쓰지 않음)
    assertThat(saved.getFixedPrice()).isEqualTo(11_000L);
    // listingDate 보존됨
    assertThat(saved.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    // 수량 갱신됨
    assertThat(saved.getOfferingShareCount()).isEqualTo(3_000_000L);
    // lastSyncedAt 갱신됨
    assertThat(saved.getLastSyncedAt()).isNotNull();
    // syncSource 유지
    assertThat(saved.getSyncSource()).isEqualTo("OPENDART");
  }

  @Test
  void sync_skipsManualRecord_whenSyncSourceNull() {
    // given: 수동 등록 레코드 존재 (syncSource == null)
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "10,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    Ipo manualRecord =
        Ipo.builder()
            .id(1L)
            .companyName("옵토닉스")
            .ipoName("옵토닉스")
            .corpCode("01658035")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(11_000L)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .offeringShareCount(2_200_000L)
            .status("ACTIVE")
            .syncSource(null) // 수동 등록
            .lastSyncedAt(null)
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now().minusDays(10))
            .build();

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01658035", "ACTIVE"))
        .thenReturn(Optional.of(manualRecord));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getSkippedManual()).isEqualTo(1);
    assertThat(result.getSkippedNonIpo()).isEqualTo(0);

    // saveAndFlush 호출되지 않아야 함 (수정하지 않음)
  }

  @Test
  void sync_skipsManualRecord_whenNameAndDateMatch() {
    // given: corpCode로 못 찾지만 (ipoName, startDate)로 수동 레코드 존재
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "10,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01658035", "ACTIVE"))
        .thenReturn(Optional.empty());

    Ipo manualRecord =
        Ipo.builder()
            .id(1L)
            .companyName("옵토닉스")
            .ipoName("옵토닉스")
            .corpCode("99999999") // 다른 corpCode
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .status("ACTIVE")
            .syncSource(null) // 수동 등록
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now().minusDays(10))
            .build();

    when(ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "옵토닉스", LocalDate.of(2026, 11, 5), "ACTIVE"))
        .thenReturn(Optional.of(manualRecord));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getSkippedManual()).isEqualTo(1);
    assertThat(result.getSkippedNonIpo()).isEqualTo(0);
  }

  @Test
  void sync_skipsNonIpo_whenStockCodePresent() {
    // given: stock_code가 있는 회사 (유상증자 추정)
    OpenDartDisclosureItem company = createCompany("01042650", "큐리언트", "115180");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getSkippedManual()).isEqualTo(0);
    assertThat(result.getSkippedNonIpo()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(0);

    // DS006 호출되지 않아야 함
    verify(equitySecuritiesClient, org.mockito.Mockito.never())
        .getEquitySecurityDetail(anyString());
  }

  @Test
  void sync_recordsFailure_whenSubscriptionStartDateNull() {
    // given: sbd 파싱 실패로 subscriptionStartDate가 null
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("01658035")
            .corpName("옵토닉스")
            .sbd("알 수 없는 형식")
            .build();
    detail.parseDerivedFields(); // startDate = null
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getFailures()).hasSize(1);
    assertThat(result.getFailures().get(0).getReason()).contains("청약 시작일 파싱 실패");
    assertThat(result.getFailures().get(0).getCorpCode()).isEqualTo("01658035");
  }

  @Test
  void sync_continuesOnSingleFailure() {
    // given: 첫 번째 회사 실패, 두 번째 회사 성공
    OpenDartDisclosureItem company1 = createCompany("01658035", "옵토닉스", "");
    OpenDartDisclosureItem company2 = createCompany("01028933", "멜콘", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company1, company2));

    // 첫 번째: DS006 빈 응답
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of());

    // 두 번째: 정상
    OpenDartEquitySecurityDetail detail2 =
        createDetail("01028933", "멜콘", "2026년 11월 10일 ~ 2026년 11월 11일", "1,500,000", "20,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01028933")).thenReturn(List.of(detail2));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01028933", "ACTIVE"))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "멜콘", LocalDate.of(2026, 11, 10), "ACTIVE"))
        .thenReturn(Optional.empty());
    when(businessDayCalculator.nextBusinessDay(LocalDate.of(2026, 11, 10)))
        .thenReturn(LocalDate.of(2026, 11, 11));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(1); // 두 번째만 성공
    assertThat(result.getFailureCount()).isEqualTo(1); // 첫 번째 실패
    assertThat(result.getFailures()).hasSize(1);
    assertThat(result.getFailures().get(0).getCorpCode()).isEqualTo("01658035");
  }

  @Test
  void sync_abortsOnAuthenticationException() {
    // given: DS001에서 인증 예외
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenThrow(new OpenDartAuthenticationException("010", "미등록 키입니다."));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getAbortReason()).contains("인증 오류");
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
  }

  @Test
  void sync_abortsOnRateLimit020() {
    // given: DS001에서 rate limit 020
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenThrow(new OpenDartApiException("020", "요청 제한을 초과했습니다."));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getAbortReason()).contains("요청 제한 초과");
    assertThat(result.getCreated()).isEqualTo(0);
  }

  @Test
  void sync_recordsFailureOnApiException_continues() {
    // given: 첫 번째 회사 DS006에서 021 오류, 두 번째 정상
    OpenDartDisclosureItem company1 = createCompany("01658035", "옵토닉스", "");
    OpenDartDisclosureItem company2 = createCompany("01028933", "멜콘", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company1, company2));

    // 첫 번째: 021 오류 (인증/rate limit 아님 → 계속 진행)
    doThrow(new OpenDartApiException("021", "기타 오류"))
        .when(equitySecuritiesClient)
        .getEquitySecurityDetail("01658035");

    // 두 번째: 정상
    OpenDartEquitySecurityDetail detail2 =
        createDetail("01028933", "멜콘", "2026년 11월 10일 ~ 2026년 11월 11일", "1,500,000", "20,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01028933")).thenReturn(List.of(detail2));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01028933", "ACTIVE"))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "멜콘", LocalDate.of(2026, 11, 10), "ACTIVE"))
        .thenReturn(Optional.empty());
    when(businessDayCalculator.nextBusinessDay(LocalDate.of(2026, 11, 10)))
        .thenReturn(LocalDate.of(2026, 11, 11));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(1);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getFailures().get(0).getReason()).contains("DS006 조회 실패");
    assertThat(result.isAborted()).isFalse();
  }

  @Test
  void sync_recordsFailureOnDataIntegrityViolation_continues() {
    // given: 저장 시 중복 키 위반 예외
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "10,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));

    when(ipoRepository.findByCorpCodeAndStatusAndDeletedAtIsNull("01658035", "ACTIVE"))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
            "옵토닉스", LocalDate.of(2026, 11, 5), "ACTIVE"))
        .thenReturn(Optional.empty());
    when(businessDayCalculator.nextBusinessDay(LocalDate.of(2026, 11, 5)))
        .thenReturn(LocalDate.of(2026, 11, 6));

    // saveAndFlush에서 예외 발생 시뮬레이션
    doThrow(new DataIntegrityViolationException("중복 키 위반", null))
        .when(ipoRepository)
        .saveAndFlush(any(Ipo.class));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getFailures().get(0).getReason()).contains("처리 중 예외");
  }

  @Test
  void sync_emptyDs001Result_returnsEmptyResult() {
    // given: DS001 빈 결과
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7)).thenReturn(List.of());

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(0);
    assertThat(result.isAborted()).isFalse();
  }

  @Test
  void sync_ds006EmptyList_recordsFailure() {
    // given: DS006 빈 리스트 반환
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of());

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getFailures().get(0).getReason()).contains("DS006 상세 정보 없음");
  }
}
