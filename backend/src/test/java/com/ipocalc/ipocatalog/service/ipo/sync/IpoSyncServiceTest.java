package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTerms;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTermsParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartAuthenticationException;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartDisclosureItem;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.ipocalc.ipocatalog.service.ipo.sync.BusinessDayCalculator;
import com.ipocalc.ipocatalog.service.ipo.sync.IpoSyncRunRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link IpoSyncService} 단위 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class IpoSyncServiceTest {

  @MockitoBean private OpenDartCorpDisclosureClient corpDisclosureClient;

  @MockitoBean private OpenDartEquitySecuritiesClient equitySecuritiesClient;

  @MockitoBean private DartIpoFilingClient dartIpoFilingClient;

  @MockitoBean private DartOfferingTermsParser offeringTermsParser;

  @MockitoBean private IpoRepository ipoRepository;

  @MockitoBean private BusinessDayCalculator businessDayCalculator;

  @MockitoBean private IpoScheduleMergeService ipoScheduleMergeService;

  @MockitoBean private UstockPlusCalendarClient ustockPlusCalendarClient;

  @MockitoBean private IpoSyncRunRecorder recorder;

  // TransactionTemplate은 실제 빈 사용 (Mock으로 람다 인자 캡처 시 타입 문제 발생)
  @Autowired private TransactionTemplate transactionTemplate;

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), SEOUL);

  private IpoSyncService syncService;

  private final ArgumentCaptor<Ipo> ipoCaptor = ArgumentCaptor.forClass(Ipo.class);

  @BeforeEach
  void setUp() {
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of());
    IpoSyncPersistence persistence =
        new IpoSyncPersistence(ipoRepository, businessDayCalculator, FIXED_CLOCK, equitySecuritiesClient);
    IpoReconciliationService reconciliationService =
        new IpoReconciliationService(
            corpDisclosureClient,
            equitySecuritiesClient,
            dartIpoFilingClient,
            offeringTermsParser,
            ipoRepository,
            persistence,
            transactionTemplate,
            FIXED_CLOCK);
    syncService =
        new IpoSyncService(
            corpDisclosureClient,
            equitySecuritiesClient,
            dartIpoFilingClient,
            offeringTermsParser,
            persistence,
            transactionTemplate,
            ipoScheduleMergeService,
            reconciliationService,
            recorder,
            FIXED_CLOCK);
  }

  private OpenDartDisclosureItem createCompany(String corpCode, String corpName, String stockCode) {
    return createCompany(corpCode, corpName, stockCode, "증권신고서(지분증권)");
  }

  private OpenDartDisclosureItem createCompany(
      String corpCode, String corpName, String stockCode, String reportName) {
    return OpenDartDisclosureItem.builder()
        .corpCode(corpCode)
        .corpName(corpName)
        .stockCode(stockCode)
        .reportNm(reportName)
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
  void withdrawnReceiptMustMatchBeforeDeactivation() {
    var company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .rceptNo("receipt")
            .rm("철")
            .reportNm("증권신고서(지분증권)")
            .build();
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("receipt")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    var ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(ipo));
    assertThat(syncService.sync(7).getUpdated()).isEqualTo(1);
    assertThat(ipo.getDeletedAt()).isNotNull();
  }

  @Test
  void differentReceiptCannotWithdrawAnotherOffering() {
    var company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .rceptNo("withdrawn")
            .rm("철")
            .reportNm("증권신고서(지분증권)")
            .build();
    var detail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("new-offering")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));
    assertThat(syncService.sync(7).getFailureCount()).isEqualTo(1);
    verify(ipoRepository, never()).saveAndFlush(any());
  }

  @Test
  void withdrawnSelectsDetailByOriginalReceiptNotLatest() {
    // given: 원공시 rm=철, DS006에 원공시(작은 rceptNo)와 철회신고서(큰 rceptNo) 둘 다 있음
    var company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .rceptNo("20261012000149") // 원공시 접수번호
            .rm("철")
            .reportNm("증권신고서(지분증권)")
            .build();
    var originalDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149") // 원공시와 일치
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    var withdrawalNoticeDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261030000200") // 철회신고서(더 최신)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    var ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp"))
        .thenReturn(List.of(withdrawalNoticeDetail, originalDetail)); // 최신이 앞에 있어도 원공시 선택돼야 함
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(ipo));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 원공시 접수번호로 매칭되어 철회 처리됨
    assertThat(result.getUpdated()).isEqualTo(1);
    assertThat(ipo.getDeletedAt()).isNotNull();
    assertThat(ipo.getStatus()).isEqualTo(RecordStatus.INACTIVE);
  }

  @Test
  void withdrawnWithRealSbdParsingSelectsCorrectDetail() {
    // given: 실제 sbd 파싱 기반 케이스 - "2026년 11월 05일" 단일일자
    var company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .rceptNo("20261012000149")
            .rm("철")
            .reportNm("증권신고서(지분증권)")
            .build();
    var originalDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .sbd("2026년 11월 05일")
            .build();
    originalDetail.parseDerivedFields(); // subscriptionStartDate=2026-11-05
    var withdrawalNoticeDetail =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261030000200")
            .sbd("2026년 11월 05일")
            .build();
    withdrawalNoticeDetail.parseDerivedFields();
    var ipo =
        Ipo.builder()
            .syncSource("OPENDART")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .build();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp"))
        .thenReturn(List.of(withdrawalNoticeDetail, originalDetail));
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(ipo));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: parseDerivedFields 후에도 원공시 접수번호로 매칭되어 철회 처리됨
    assertThat(result.getUpdated()).isEqualTo(1);
    assertThat(ipo.getDeletedAt()).isNotNull();
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

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of());

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
    assertThat(saved.getStatus()).isEqualTo(RecordStatus.ACTIVE);
  }

  private OpenDartEquitySecurityDetail createDetailWithDates(String asand, String pymd) {
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("01658035")
            .corpName("옵토닉스")
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .stkcnt("2,200,000")
            .slprc("10,000")
            .asand(asand)
            .pymd(pymd)
            .build();
    detail.parseDerivedFields();
    return detail;
  }

  @Test
  void sync_createsNewIpo_withAllotmentAndRefundDateFromDart() {
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(createCompany("01658035", "옵토닉스", "")));
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035"))
        .thenReturn(List.of(createDetailWithDates("2026년 11월 10일", "2026년 11월 10일")));
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of());

    syncService.sync(7);

    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(ipoCaptor.getValue().getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 10));
  }

  @Test
  void sync_updatesExistingIpo_dartDatesOverrideOldValues_butDashKeepsExisting() {
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(createCompany("01658035", "옵토닉스", "")));
    // 배정일은 DART가 값을 주고, 환불일은 "-"라 읽을 수 없음
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035"))
        .thenReturn(List.of(createDetailWithDates("2026년 11월 10일", "-")));
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .companyName("옵토닉스")
            .ipoName("옵토닉스")
            .corpCode("01658035")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .allotmentDate(LocalDate.of(2026, 11, 9)) // 예전 ustockplus 값
            .refundDate(LocalDate.of(2026, 11, 11)) // 예전 ustockplus 값
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART+USTOCKPLUS")
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    syncService.sync(7);

    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(ipoCaptor.getValue().getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 11));
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

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of());

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
  void sync_setsFixedPriceOnlyForFinalizedDisclosure() {
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "", "[발행조건확정]증권신고서(지분증권)");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "12,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(businessDayCalculator.nextBusinessDay(LocalDate.of(2026, 11, 5)))
        .thenReturn(LocalDate.of(2026, 11, 6));

    IpoSyncResult result = syncService.sync(7);

    assertThat(result.getCreated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getFixedPrice()).isEqualTo(12_000L);
  }

  @Test
  void sync_fillsMissingFixedPriceWhenExistingRecordReceivesFinalizedDisclosure() {
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "", "[발행조건확정]증권신고서(지분증권)");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "12,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .companyName("옵토닉스")
            .ipoName("옵토닉스")
            .corpCode("01658035")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(null)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART")
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    IpoSyncResult result = syncService.sync(7);

    assertThat(result.getUpdated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getFixedPrice()).isEqualTo(12_000L);
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
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART")
            .lastSyncedAt(LocalDateTime.now().minusDays(1))
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now().minusDays(1))
            .build();

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

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
            .status(RecordStatus.ACTIVE)
            .syncSource(null) // 수동 등록
            .lastSyncedAt(null)
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now().minusDays(10))
            .build();

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of(manualRecord));

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

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());

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
            .status(RecordStatus.ACTIVE)
            .syncSource(null) // 수동 등록
            .createdAt(LocalDateTime.now().minusDays(10))
            .updatedAt(LocalDateTime.now().minusDays(10))
            .build();

    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of(manualRecord));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getUpdated()).isEqualTo(0);
    assertThat(result.getSkippedManual()).isEqualTo(1);
    assertThat(result.getSkippedNonIpo()).isEqualTo(0);
  }

  @Test
  void sync_processesStockCodePresentCompany_whenDS006Empty() {
    // given: stock_code가 있어도 IPO 후보로 동기화 시도
    OpenDartDisclosureItem company = createCompany("01042650", "큐리언트", "115180");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));

    // DS006 빈 응답
    when(equitySecuritiesClient.getEquitySecurityDetail("01042650")).thenReturn(List.of());

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: skippedNonIpo가 아니고 DS006 빈 응답으로 failure 처리
    assertThat(result.getCreated()).isEqualTo(0);
    assertThat(result.getSkippedNonIpo()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getFailures().get(0).getReason()).contains("DS006 상세 정보 없음");
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

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01028933", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 10), RecordStatus.ACTIVE))
        .thenReturn(List.of());
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

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01028933", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 10), RecordStatus.ACTIVE))
        .thenReturn(List.of());
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

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of());
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

  @Test
  void sync_abortsWithRetryableTrue_onGeneralApiExceptionInDs001() {
    // given: DS001에서 일반 API 오류 (예: status 800 시스템 점검)
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenThrow(new OpenDartApiException("800", "시스템 점검 중입니다."));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 중단되고 재시도 가능으로 표시
    assertThat(result.isAborted()).isTrue();
    assertThat(result.isRetryable()).isTrue();
    assertThat(result.getAbortReason()).contains("API 오류");
    assertThat(result.getCreated()).isEqualTo(0);
  }

  @Test
  void sync_abortsWithRetryableFalse_onAuthenticationExceptionInDs001() {
    // given: DS001에서 인증 예외
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenThrow(new OpenDartAuthenticationException("010", "미등록 키입니다."));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 중단되고 재시도 불가로 표시
    assertThat(result.isAborted()).isTrue();
    assertThat(result.isRetryable()).isFalse();
    assertThat(result.getAbortReason()).contains("인증 오류");
  }

  @Test
  void sync_abortsWithRetryableFalse_onRateLimit020InDs001() {
    // given: DS001에서 rate limit 020
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenThrow(new OpenDartApiException("020", "요청 제한을 초과했습니다."));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 중단되고 재시도 불가로 표시 (그날 중단)
    assertThat(result.isAborted()).isTrue();
    assertThat(result.isRetryable()).isFalse();
    assertThat(result.getAbortReason()).contains("요청 제한 초과");
  }

  @Test
  void sync_recordsScheduleMerged_whenOpendartLoopCompletes() {
    // given: DS001 빈 결과(신규 공시 없음) + ustockplus 병합 3건
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7)).thenReturn(List.of());
    when(ipoScheduleMergeService.merge(any(LocalDate.class))).thenReturn(3);

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 병합 기준일은 주입된 Clock의 오늘(서울 시간대)
    assertThat(result.isAborted()).isFalse();
    assertThat(result.getScheduleMerged()).isEqualTo(3);
    verify(ipoScheduleMergeService).merge(LocalDate.of(2026, 10, 15));
  }

  @Test
  void sync_skipsScheduleMerge_whenAborted() {
    // given: DS001 인증 예외로 중단
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenThrow(new OpenDartAuthenticationException("010", "미등록 키입니다."));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 중단 시에는 병합을 호출하지 않음
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getScheduleMerged()).isEqualTo(0);
    verify(ipoScheduleMergeService, never()).merge(any(LocalDate.class));
  }

  @Test
  void sync_keepsOpendartResult_whenScheduleMergeThrows() {
    // given: DS001 정상(빈 결과) + 병합 중 예외
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7)).thenReturn(List.of());
    when(ipoScheduleMergeService.merge(any(LocalDate.class)))
        .thenThrow(new RuntimeException("ustockplus 장애"));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: OpenDART 결과는 그대로 유지되고 병합 건수는 0
    assertThat(result.isAborted()).isFalse();
    assertThat(result.getScheduleMerged()).isEqualTo(0);
    assertThat(result.getFailureCount()).isEqualTo(0);
  }

  private IpoSyncResult syncOne(OpenDartEquitySecurityDetail... details) {
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(createCompany("01658035", "옵토닉스", "")));
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(details));
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(businessDayCalculator.nextBusinessDay(any())).thenReturn(LocalDate.of(2026, 11, 6));
    return syncService.sync(7);
  }

  @Test
  void sync_unreadableEndDate_recordsFailureInsteadOfNextBusinessDay() {
    IpoSyncResult result =
        syncOne(createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ -", "1,000", "10,000"));

    assertThat(result.getFailureCount()).isEqualTo(1);
    assertThat(result.getCreated()).isEqualTo(0);
    verify(ipoRepository, never()).saveAndFlush(any());
  }

  @Test
  void sync_endBeforeStart_recordsFailure() {
    IpoSyncResult result =
        syncOne(
            createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 04일", "1,000", "10,000"));

    assertThat(result.getFailureCount()).isEqualTo(1);
    verify(ipoRepository, never()).saveAndFlush(any());
  }

  @Test
  void sync_endWithoutYearAndMonth_isCompletedFromStart() {
    IpoSyncResult result =
        syncOne(createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 6일", "1,000", "10,000"));

    assertThat(result.getCreated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
  }

  @Test
  void sync_sameCompanyWithDifferentNameFormat_isNotCreatedTwice() {
    Ipo manual =
        Ipo.builder()
            .companyName("(주) 옵토닉스")
            .ipoName("(주) 옵토닉스")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .status(RecordStatus.ACTIVE)
            .build();
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of(manual));

    IpoSyncResult result =
        syncOne(
            createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "1,000", "10,000"));

    assertThat(result.getSkippedManual()).isEqualTo(1);
    verify(ipoRepository, never()).saveAndFlush(any());
  }

  @Test
  void sync_multipleFilings_usesHighestReceiptNumber() {
    OpenDartEquitySecurityDetail older =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20260901000001")
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .slprc("9,000")
            .build();
    older.parseDerivedFields();
    OpenDartEquitySecurityDetail newer =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261001000002")
            .sbd("2026년 11월 12일 ~ 2026년 11월 13일")
            .slprc("12,000")
            .build();
    newer.parseDerivedFields();

    syncOne(newer, older);

    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getSubscriptionStartDate())
        .isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(ipoCaptor.getValue().getUpperBandPrice()).isEqualTo(12_000L);
  }

  @Test
  void runExclusively_rejectsOtherThreadButAllowsReentry() throws Exception {
    String reentered = syncService.runExclusively(() -> syncService.runExclusively(() -> "ok"));
    assertThat(reentered).isEqualTo("ok");

    java.util.concurrent.atomic.AtomicReference<Throwable> other =
        new java.util.concurrent.atomic.AtomicReference<>();
    syncService.runExclusively(
        () -> {
          Thread t =
              new Thread(
                  () -> {
                    try {
                      syncService.runExclusively(() -> null);
                    } catch (RuntimeException e) {
                      other.set(e);
                    }
                  });
          t.start();
          try {
            t.join();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return null;
        });

    assertThat(other.get())
        .isInstanceOf(com.ipocalc.ipocatalog.exception.IpoSyncBusyException.class);
  }

  @Test
  void sync_reconcileExceptionDoesNotStopNewFilingCollection() {
    // given: 재점검 단계(대상 조회)에서 예외 발생 → 신규 수집은 계속되어야 한다
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenThrow(new RuntimeException("boom"));
    OpenDartDisclosureItem company = createCompany("01658035", "옵토닉스", "");
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    OpenDartEquitySecurityDetail detail =
        createDetail("01658035", "옵토닉스", "2026년 11월 05일 ~ 2026년 11월 06일", "2,200,000", "10,000");
    when(equitySecuritiesClient.getEquitySecurityDetail("01658035")).thenReturn(List.of(detail));
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull(
            "01658035", RecordStatus.ACTIVE))
        .thenReturn(List.of());
    when(ipoRepository.findBySubscriptionStartDateAndStatusAndDeletedAtIsNull(
            LocalDate.of(2026, 11, 5), RecordStatus.ACTIVE))
        .thenReturn(List.of());

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 철회 점검 실패는 기록되지만 신규 수집은 정상 완료된다
    assertThat(result.getCreated()).isEqualTo(1);
    assertThat(result.getFailures())
        .anySatisfy(failure -> assertThat(failure.getReason()).contains("철회점검:"));
  }

  @Test
  void sync_reconcileAuthAbortStopsNewFilingCollection() {
    // given: 활성 자동 공모가 있고 재점검 중 인증 오류 발생 → 같은 키를 쓰므로 신규 수집도 중단해야 한다
    Ipo row =
        Ipo.builder()
            .id(1L)
            .corpCode("corp")
            .companyName("심원")
            .ipoName("심원")
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenReturn(List.of(row));
    when(corpDisclosureClient.searchAllByDateRange(
            org.mockito.ArgumentMatchers.eq("corp"), any(), any()))
        .thenThrow(new OpenDartAuthenticationException("011", "인증 오류"));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 인증 오류는 재시도 없이 중단되고 신규 수집은 호출되지 않는다
    assertThat(result.isAborted()).isTrue();
    assertThat(result.getFailureCount()).isZero();
    verify(corpDisclosureClient, never()).searchRecentEquitySecuritiesCompanies(7);
  }

  @Test
  void recoverAfterDeploy_reconcileExceptionIsReportedAsFailure_notSuccess() {
    // given: 재점검 대상 조회에서 예외 → 복구가 SUCCESS로 마킹되면 안 된다
    when(ipoRepository.findByStatusAndSyncSourceIsNotNullAndDeletedAtIsNull(RecordStatus.ACTIVE))
        .thenThrow(new RuntimeException("boom"));

    // when
    syncService.recoverAfterDeploy();

    // then: recordRecover 에는 failed >= 1 이 전달된다 (가짜 성공 금지)
    org.mockito.ArgumentCaptor<IpoReconcileResult> captor =
        org.mockito.ArgumentCaptor.forClass(IpoReconcileResult.class);
    verify(recorder).recordRecover(org.mockito.ArgumentMatchers.any(), captor.capture());
    assertThat(captor.getValue().getFailed()).isGreaterThanOrEqualTo(1);
    assertThat(captor.getValue().getFailures())
        .anySatisfy(failure -> assertThat(failure.getReason()).contains("RuntimeException"));
  }

  private static final LocalDate FIRST = LocalDate.of(2026, 11, 5);

  @Test
  void sync_selectsFinalizedReportDetailWhenCompanyIsFinalized() {
    // given: company는 발행조건확정 공시(rceptNo=FINAL),
    // DS006에는 FINAL(발행조건확정)과 LATER(정정공시, 더 큰 rceptNo) 둘 다 있음
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .rceptNo("20261101000001") // FINAL
            .build();

    OpenDartEquitySecurityDetail finalizedDetail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("20261101000001") // company와 같은 rceptNo
            .build();
    finalizedDetail.parseDerivedFields();

    OpenDartEquitySecurityDetail laterDetail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("9,400")
            .rceptNo("20261115000002") // 더 큰 rceptNo (정정공시)
            .build();
    laterDetail.parseDerivedFields();

    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp"))
        .thenReturn(List.of(laterDetail, finalizedDetail)); // 순서 뒤섞여도 FINAL 선택돼야 함

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of());

    // 원문 파싱 모킹: 발행조건확정 공시의 원문에서 밴드 10,000~11,000, 확정가 11,000 반환
    when(dartIpoFilingClient.downloadDocument("20261101000001")).thenReturn(java.util.Optional.of("<xml>dummy</xml>"));
    DartOfferingTerms terms = new DartOfferingTerms("20261101000001", 1, 10_000L, 11_000L, 11_000L,
        LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), null, null, 1_000_000L);
    when(offeringTermsParser.parse(anyString(), eq("20261101000001"), eq(true))).thenReturn(terms);

    // when
    syncService.sync(7);

    // then: FINAL 공시의 상세(확정공모가 11,000)가 선택되어 저장됨
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getFixedPrice()).isEqualTo(11_000L);
    assertThat(ipoCaptor.getValue().getLowerBandPrice()).isEqualTo(10_000L);
    assertThat(ipoCaptor.getValue().getUpperBandPrice()).isEqualTo(11_000L);
  }

  @Test
  void sync_updatesFixedPriceOnFinalizedReportForAutoSyncRecord() {
    // given: 기존 자동 동기화 레코드에 fixedPrice=9400(잘못된 값)
    Ipo existing =
        Ipo.builder()
            .syncSource("OPENDART")
            .fixedPrice(9_400L) // 잘못 저장된 값
            .subscriptionStartDate(FIRST)
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    // 발행조건확정 공시로 확정공모가 11000 옴
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .rceptNo("20261101000001")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("20261101000001")
            .build();
    detail.parseDerivedFields();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));

    // 원문 파싱 모킹: 발행조건확정 공시의 원문에서 확정가 11,000 반환
    when(dartIpoFilingClient.downloadDocument("20261101000001")).thenReturn(java.util.Optional.of("<xml>dummy</xml>"));
    DartOfferingTerms terms = new DartOfferingTerms("20261101000001", 1, 10_000L, 11_000L, 11_000L,
        LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), null, null, 1_000_000L);
    when(offeringTermsParser.parse(anyString(), eq("20261101000001"), eq(true))).thenReturn(terms);

    // when
    syncService.sync(7);

    // then: 자동 동기화 레코드는 확정공모 공시로 fixedPrice 강제 업데이트
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getFixedPrice()).isEqualTo(11_000L);
  }

  @Test
  void sync_protectsManualRecordFixedPriceEvenOnFinalizedReport() {
    // given: 수동 레코드(syncSource=null) fixedPrice=10000
    Ipo manual =
        Ipo.builder()
            .syncSource(null) // 수동
            .fixedPrice(10_000L)
            .subscriptionStartDate(FIRST)
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(manual));

    // 발행조건확정 공시로 확정공모가 12000 옴
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .rceptNo("20261101000001")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("12,000")
            .rceptNo("20261101000001")
            .build();
    detail.parseDerivedFields();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 수동 레코드는 SKIPPED_MANUAL로 처리되어 saveAndFlush 호출되지 않음
    // (수동 등록 레코드는 자동 동기화가 덮어쓰지 않음)
    assertThat(result.getSkippedManual()).isEqualTo(1);
    verify(ipoRepository, never()).saveAndFlush(any());
  }

  @Test
  void sync_updatesBandPriceEvenWhenDifferentReceiptNotSameOffering() {
    // given: 기존 자동 동기화 레코드(source_receipt_no=OLD, fixedPrice=9400, 밴드 10000)
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .corpCode("corp")
            .companyName("name")
            .ipoName("name")
            .corpCode("corp")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(9_400L)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .sourceReceiptNo("OLD-RECEIPT")
            .syncSource("OPENDART")
            .status(RecordStatus.ACTIVE)
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    // 새 공시: 다른 접수번호, 일반 공시(정정 아님), 밴드 가격이 11000으로 변경
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("NEW-RECEIPT")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("NEW-RECEIPT")
            .build();
    detail.parseDerivedFields();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 전체 갱신은 보류되지만 밴드 가격·주식 수는 갱신되고, source_receipt_no·fixedPrice는 미변경
    assertThat(result.getUpdated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getLowerBandPrice()).isEqualTo(11_000L);
    assertThat(saved.getUpperBandPrice()).isEqualTo(11_000L);
    assertThat(saved.getOfferingShareCount()).isEqualTo(1_000_000L);
    assertThat(saved.getSourceReceiptNo()).isEqualTo("OLD-RECEIPT"); // 미변경
    assertThat(saved.getFixedPrice()).isEqualTo(9_400L); // 일반 공시이므로 fixedPrice 미변경
  }

  @Test
  void sync_doesNotUpdateFixedPriceWhenNotFinalizedReport() {
    // given: 기존 자동 동기화 레코드, 일반 공시
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .corpCode("corp")
            .companyName("name")
            .ipoName("name")
            .corpCode("corp")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(null)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .sourceReceiptNo("OLD-RECEIPT")
            .syncSource("OPENDART")
            .status(RecordStatus.ACTIVE)
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    // general disclosure(정정 아님), confirmedPrice는 null이어야 함
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("증권신고서(지분증권)")
            .rceptNo("NEW-RECEIPT")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("NEW-RECEIPT")
            .build();
    detail.parseDerivedFields();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 일반 공시이므로 fixedPrice는 null 유지
    assertThat(result.getUpdated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getFixedPrice()).isNull();
  }

  @Test
  void sync_selectsCorrectionReportDetailWhenCompanyIsCorrectionReport() {
    // given: company는 기재정정 공시(rceptNo=CORRECTION),
    // DS006에는 CORRECTION(기재정정)과 LATER(일반 공시, 더 큰 rceptNo) 둘 다 있음
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("[기재정정]증권신고서(지분증권)")
            .rceptNo("20261101000001") // CORRECTION
            .build();

    OpenDartEquitySecurityDetail correctionDetail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("20261101000001") // company와 같은 rceptNo
            .build();
    correctionDetail.parseDerivedFields();

    OpenDartEquitySecurityDetail laterDetail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일")
            .stkcnt("1,000,000")
            .slprc("9,400")
            .rceptNo("20261115000002") // 더 큰 rceptNo (일반 공시)
            .build();
    laterDetail.parseDerivedFields();

    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp"))
        .thenReturn(List.of(laterDetail, correctionDetail)); // 순서 뒤섞여도 CORRECTION 선택돼야 함

    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of());

    // 원문 파싱 모킹: 기재정정 공시의 원문에서 밴드 10,000~11,000 반환 (fixedPrice는 null)
    // correction report이므로 isFinalizedReport() = false
    when(dartIpoFilingClient.downloadDocument("20261101000001")).thenReturn(java.util.Optional.of("<xml>dummy</xml>"));
    DartOfferingTerms terms = new DartOfferingTerms("20261101000001", 1, 10_000L, 11_000L, null,
        LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), null, null, 1_000_000L);
    when(offeringTermsParser.parse(anyString(), eq("20261101000001"), eq(false))).thenReturn(terms);

    // when
    syncService.sync(7);

    // then: 기재정정 공시의 상세(공모가 11,000)가 선택되어 저장됨
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    assertThat(ipoCaptor.getValue().getFixedPrice()).isNull(); // 기재정정은 확정가가 아니므로 fixedPrice null
    assertThat(ipoCaptor.getValue().getLowerBandPrice()).isEqualTo(10_000L); // 밴드 하한
    assertThat(ipoCaptor.getValue().getUpperBandPrice()).isEqualTo(11_000L); // 밴드 상한
  }

  @Test
  void sync_updatesBandPriceWhenCorrectionReportAndDifferentReceipt() {
    // given: 기존 자동 동기화 레코드, 기재정정 공시
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .corpCode("corp")
            .companyName("name")
            .ipoName("name")
            .corpCode("corp")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(9_400L)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .sourceReceiptNo("OLD-RECEIPT")
            .syncSource("OPENDART")
            .status(RecordStatus.ACTIVE)
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    // 기재정정 공시: 밴드 가격 갱신, fixedPrice는 변경하지 않음(확정가 아님)
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("[기재정정]증권신고서(지분증권)")
            .rceptNo("NEW-RECEIPT")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("NEW-RECEIPT")
            .build();
    detail.parseDerivedFields();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 기재정정 공시이므로 밴드 가격 갱신, fixedPrice는 기존 값 유지(확정가 아님), source_receipt_no 미변경
    assertThat(result.getUpdated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getFixedPrice()).isEqualTo(9_400L); // 기재정정은 확정가가 아니므로 기존 fixedPrice 유지
    assertThat(saved.getLowerBandPrice()).isEqualTo(11_000L);
    assertThat(saved.getUpperBandPrice()).isEqualTo(11_000L);
    assertThat(saved.getSourceReceiptNo()).isEqualTo("OLD-RECEIPT"); // 미변경
  }

  @Test
  void sync_updatesFixedPriceWhenFinalizedReportAndDifferentReceipt() {
    // given: 기존 자동 동기화 레코드, 발행조건확정 공시
    Ipo existing =
        Ipo.builder()
            .id(1L)
            .corpCode("corp")
            .companyName("name")
            .ipoName("name")
            .corpCode("corp")
            .lowerBandPrice(10_000L)
            .upperBandPrice(10_000L)
            .fixedPrice(9_400L)
            .subscriptionStartDate(LocalDate.of(2026, 11, 5))
            .subscriptionEndDate(LocalDate.of(2026, 11, 6))
            .sourceReceiptNo("OLD-RECEIPT")
            .syncSource("OPENDART")
            .status(RecordStatus.ACTIVE)
            .build();
    when(ipoRepository.findAllByCorpCodeAndStatusAndDeletedAtIsNull("corp", RecordStatus.ACTIVE))
        .thenReturn(List.of(existing));

    // 발행조건확정 공시: confirmedPrice 설정되어야 함
    OpenDartDisclosureItem company =
        OpenDartDisclosureItem.builder()
            .corpCode("corp")
            .corpName("name")
            .stockCode("")
            .reportNm("[발행조건확정]증권신고서(지분증권)")
            .rceptNo("NEW-RECEIPT")
            .build();
    OpenDartEquitySecurityDetail detail =
        OpenDartEquitySecurityDetail.builder()
            .corpCode("corp")
            .corpName("name")
            .sbd("2026년 11월 05일 ~ 2026년 11월 06일")
            .stkcnt("1,000,000")
            .slprc("11,000")
            .rceptNo("NEW-RECEIPT")
            .build();
    detail.parseDerivedFields();
    when(corpDisclosureClient.searchRecentEquitySecuritiesCompanies(7))
        .thenReturn(List.of(company));
    when(equitySecuritiesClient.getEquitySecurityDetail("corp")).thenReturn(List.of(detail));

    // when
    IpoSyncResult result = syncService.sync(7);

    // then: 발행조건확정 공시이므로 fixedPrice 갱신, 밴드 가격도 갱신, source_receipt_no 미변경
    assertThat(result.getUpdated()).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getFixedPrice()).isEqualTo(11_000L);
    assertThat(saved.getLowerBandPrice()).isEqualTo(11_000L);
    assertThat(saved.getUpperBandPrice()).isEqualTo(11_000L);
    assertThat(saved.getSourceReceiptNo()).isEqualTo("OLD-RECEIPT"); // 미변경
  }
}
