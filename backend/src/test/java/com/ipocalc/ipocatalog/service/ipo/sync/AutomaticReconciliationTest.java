package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.accountauthentication.domain.model.account.Account;
import com.ipocalc.accountauthentication.repository.account.AccountRepository;
import com.ipocalc.allocationmanagement.domain.model.allocation.Allocation;
import com.ipocalc.allocationmanagement.repository.allocation.AllocationRepository;
import com.ipocalc.investormanagement.domain.model.investor.Investor;
import com.ipocalc.investormanagement.repository.investor.InvestorRepository;
import com.ipocalc.ipocatalog.controller.ipo.dto.IpoUpdateRequest;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.domain.model.ipo.IpoSyncRun;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.repository.ipo.IpoSyncRunRepository;
import com.ipocalc.ipocatalog.service.ipo.IpoService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartOfferingTermsParser;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiProperties;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartEquitySecuritiesClient;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.dto.OpenDartEquitySecurityDetail;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusScheduleDates;
import com.ipocalc.salemanagement.domain.model.sale.Sale;
import com.ipocalc.salemanagement.repository.sale.SaleRepository;
import com.ipocalc.subscriptionmanagement.domain.model.subscription.Subscription;
import com.ipocalc.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 활성 자동 공모 재점검(원공시 rm=철 재확인·일정 정정·배포 후 복구)의 통합 테스트.
 *
 * <p>OpenDART 실응답은 조회하지 않고 고정 fixture(simwon 실공시 값: corp 02038432, 원공시 20261012000149, sbd
 * 2026-11-05)만 사용하며, 파서({@link OpenDartCorpDisclosureClient}/{@link
 * OpenDartEquitySecuritiesClient})부터 배치 진입 ({@link IpoSyncService#sync(int)} / {@link
 * IpoSyncService#recoverAfterDeploy()})까지 실제 흐름으로 검증한다.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class AutomaticReconciliationTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final String SIMWON_CORP = "02038432";
  private static final String SIMWON_ORIGINAL_RECEIPT = "20261012000149";
  private static final String LEGACY_CORP = "08213333";
  private static final String DUPLICATE_CORP = "99990002";
  private static final String MISMATCH_CORP = "99990003";

  private final List<Long> createdIpoIds = new ArrayList<>();
  private final List<Long> createdSubscriptionIds = new ArrayList<>();
  private final List<Long> createdAllocationIds = new ArrayList<>();
  private final List<Long> createdSaleIds = new ArrayList<>();
  private final List<Long> createdInvestorIds = new ArrayList<>();
  private final List<Long> createdAccountIds = new ArrayList<>();

  @Autowired private IpoRepository ipoRepository;
  @Autowired private IpoSyncRunRepository ipoSyncRunRepository;
  @Autowired private SubscriptionRepository subscriptionRepository;
  @Autowired private AllocationRepository allocationRepository;
  @Autowired private SaleRepository saleRepository;
  @Autowired private InvestorRepository investorRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private Clock clock;
  @Autowired private IpoSyncPersistence ipoSyncPersistence;
  @Autowired private IpoService ipoService;

  @AfterEach
  void cleanUp() {
    ipoSyncRunRepository.deleteAll();
    saleRepository.deleteAllById(createdSaleIds);
    allocationRepository.deleteAllById(createdAllocationIds);
    subscriptionRepository.deleteAllById(createdSubscriptionIds);
    // 소프트삭제된 행을 포함해 테스트가 만든 공모주를 물리 삭제한다
    List<Long> ipoIds = new ArrayList<>(createdIpoIds);
    ipoRepository.findAll().stream()
        .filter(ipo -> isTestCorp(ipo.getCorpCode()))
        .map(Ipo::getId)
        .forEach(ipoIds::add);
    ipoRepository.deleteAllById(ipoIds);
    investorRepository.deleteAllById(createdInvestorIds);
    accountRepository.deleteAllById(createdAccountIds);
  }

  private boolean isTestCorp(String corpCode) {
    return SIMWON_CORP.equals(corpCode)
        || LEGACY_CORP.equals(corpCode)
        || DUPLICATE_CORP.equals(corpCode)
        || MISMATCH_CORP.equals(corpCode);
  }

  private String readFixture(String fileName) throws IOException {
    return Files.readString(Path.of("src/test/resources/opendart", fileName));
  }

  private void expectGet(MockRestServiceServer server, String matcher, String fixture)
      throws IOException {
    server
        .expect(requestTo(containsString(matcher)))
        .andRespond(withSuccess(readFixture(fixture), MediaType.APPLICATION_JSON));
  }

  private void expectGet(MockRestServiceServer server, Matcher<String> matcher, String fixture)
      throws IOException {
    server
        .expect(requestTo(matcher))
        .andRespond(withSuccess(readFixture(fixture), MediaType.APPLICATION_JSON));
  }

  private IpoSyncService newSyncService(
      String ds001FullCorpFixture,
      String ds001FullCorpMatcher,
      String ds006Fixture,
      String ds006Matcher,
      String ds001RecentFixture)
      throws IOException {
    // DS001 서버 (전체 기간 재점검 1회 + 90일 최근 수집 1회)
    RestTemplate ds001Template = new RestTemplate();
    MockRestServiceServer ds001Server =
        MockRestServiceServer.bindTo(ds001Template).ignoreExpectOrder(true).build();
    RestClient ds001Client =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(ds001Template.getRequestFactory())
            .build();
    if (ds001FullCorpFixture != null) {
      expectGet(ds001Server, ds001FullCorpMatcher, ds001FullCorpFixture);
      // 철회 후 최신 정정의 최초제출일을 검증할 때 동일 원신고 이력을 한 번 더 조회한다.
      if ("ds001-simwon.json".equals(ds001FullCorpFixture) && ds001RecentFixture != null) {
        expectGet(ds001Server, ds001FullCorpMatcher, ds001FullCorpFixture);
      }
    }
    if (ds001RecentFixture != null) {
      expectGet(ds001Server, not(containsString("corp_code=")), ds001RecentFixture);
    }

    // DS006 서버 (전체 기간 재점검 1회 + 90일 최근 수집 1회)
    RestTemplate ds006Template = new RestTemplate();
    MockRestServiceServer ds006Server =
        MockRestServiceServer.bindTo(ds006Template).ignoreExpectOrder(true).build();
    RestClient ds006Client =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(ds006Template.getRequestFactory())
            .build();
    if (ds006Fixture != null) {
      serverExpectAllDs006(ds006Server, ds006Matcher, ds006Fixture);
    }

    OpenDartApiProperties properties =
        new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr");
    OpenDartCorpDisclosureClient corpClient =
        new OpenDartCorpDisclosureClient(
            ds001Client,
            properties,
            clock,
            new com.ipocalc.ipocatalog.service.support.HttpClientSupport.RequestThrottle(0));
    OpenDartEquitySecuritiesClient equityClient =
        new OpenDartEquitySecuritiesClient(
            ds006Client,
            properties,
            new com.ipocalc.ipocatalog.service.support.HttpClientSupport.RequestThrottle(0));

    UstockPlusCalendarClient ustockplus = mock(UstockPlusCalendarClient.class);
    when(ustockplus.fetchScheduleDates(any(), any()))
        .thenReturn(List.<UstockPlusScheduleDates>of());
    IpoScheduleMergeService mergeService =
        new IpoScheduleMergeService(ustockplus, ipoRepository, transactionTemplate, clock);
    IpoReconciliationService reconciliationService =
        new IpoReconciliationService(
            corpClient,
            equityClient,
            mock(DartIpoFilingClient.class),
            mock(DartOfferingTermsParser.class),
            ipoRepository,
            ipoSyncPersistence,
            transactionTemplate,
            clock);
    return new IpoSyncService(
        corpClient,
        equityClient,
        filingClientWithSimwonOriginalDocument(),
        new DartOfferingTermsParser(),
        ipoSyncPersistence,
        transactionTemplate,
        mergeService,
        reconciliationService,
        new IpoSyncRunRecorder(ipoSyncRunRepository, clock, transactionTemplate),
        clock);
  }

  private DartIpoFilingClient filingClientWithSimwonOriginalDocument() {
    DartIpoFilingClient filingClient = mock(DartIpoFilingClient.class);
    when(filingClient.downloadDocument(SIMWON_ORIGINAL_RECEIPT))
        .thenReturn(Optional.of("<DOCUMENT/>"));
    // 신규 수집은 DS006 원공시가 아니라 최신 DS001 기재정정 원문을 조회한다.
    when(filingClient.downloadDocument("20261028000200"))
        .thenReturn(Optional.of("<DOCUMENT>정정대상 공시서류의 최초 제출일: 2026년 10월 12일</DOCUMENT>"));
    return filingClient;
  }

  private void serverExpectAllDs006(MockRestServiceServer server, String matcher, String fixture)
      throws IOException {
    // DS006 전체 기간(재점검)과 90일 기간(최근 수집) 두 번 호출될 수 있으므로 2회 기대한다
    for (int i = 0; i < 2; i++) {
      expectGet(server, matcher, fixture);
    }
  }

  private Ipo seedAutoIpo(
      String corpCode,
      String ipoName,
      LocalDate start,
      LocalDate end,
      LocalDate listingDate,
      String sourceReceiptNo,
      LocalDate trackedStart,
      LocalDate trackedEnd,
      LocalDate trackedRefund) {
    return seedAutoIpo(
        corpCode,
        ipoName,
        start,
        end,
        listingDate,
        trackedRefund,
        sourceReceiptNo,
        trackedStart,
        trackedEnd,
        trackedRefund);
  }

  private Ipo seedAutoIpo(
      String corpCode,
      String ipoName,
      LocalDate start,
      LocalDate end,
      LocalDate listingDate,
      LocalDate refund,
      String sourceReceiptNo,
      LocalDate trackedStart,
      LocalDate trackedEnd,
      LocalDate trackedRefund) {
    LocalDateTime now = LocalDateTime.now(clock);
    Ipo ipo =
        Ipo.builder()
            .companyName(ipoName)
            .ipoName(ipoName)
            .corpCode(corpCode)
            .stockCode(null)
            .lowerBandPrice(14_000L)
            .upperBandPrice(15_000L)
            .fixedPrice(15_000L)
            .subscriptionStartDate(start)
            .subscriptionEndDate(end)
            .listingDate(listingDate)
            .refundDate(refund)
            .status(RecordStatus.ACTIVE)
            .syncSource("OPENDART")
            .sourceReceiptNo(sourceReceiptNo)
            .lastImportedSubscriptionStartDate(trackedStart)
            .lastImportedSubscriptionEndDate(trackedEnd)
            .lastImportedRefundDate(trackedRefund)
            .createdAt(now)
            .updatedAt(now)
            .build();
    Ipo saved = ipoRepository.saveAndFlush(ipo);
    createdIpoIds.add(saved.getId());
    return saved;
  }

  @Test
  void simwonReconciliation_endToEnd_fromParserToStorage() throws Exception {
    Ipo seeded =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 11, 9));
    IpoSyncService syncService =
        newSyncService(
            "ds001-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds001-simwon.json");

    IpoSyncResult result = syncService.sync(90);

    Ipo reloaded = ipoRepository.findById(seeded.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(RecordStatus.INACTIVE);
    assertThat(reloaded.getDeletedAt()).isNotNull();
    assertThat(reloaded.getWithdrawnReceiptNo()).isEqualTo(SIMWON_ORIGINAL_RECEIPT);
    // 철회는 공모 자체만 비활성화하고 이력(Subscription/Allocation/Sale)은 보존한다
    assertThat(reloaded.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(reloaded.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(result.getCreated()).isZero();
    assertThat(countActiveIpos(SIMWON_CORP)).isZero();

    IpoSyncRun daily =
        ipoSyncRunRepository
            .findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
                IpoSyncRun.RUN_DAILY_RECONCILE, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_SUCCESS)
            .orElseThrow();
    assertThat(daily.getTotalTargets()).isEqualTo(1);
    assertThat(daily.getSucceeded()).isEqualTo(1);
    assertThat(daily.getFailed()).isZero();
    assertThat(daily.getFailures()).isEmpty();
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(1);
  }

  @Test
  void legacyOffering_outside90Days_withdrawnOnRecover() throws Exception {
    Ipo legacy =
        seedAutoIpo(
            LEGACY_CORP,
            "가나다레거시",
            LocalDate.of(2025, 5, 1),
            LocalDate.of(2025, 5, 2),
            LocalDate.of(2025, 5, 20),
            null,
            null,
            null,
            LocalDate.of(2025, 5, 8));
    Ipo simwon =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 25),
            null,
            null,
            null,
            LocalDate.of(2026, 11, 9));

    RestTemplate ds001Template = new RestTemplate();
    MockRestServiceServer ds001Server =
        MockRestServiceServer.bindTo(ds001Template).ignoreExpectOrder(true).build();
    expectGet(ds001Server, "corp_code=" + SIMWON_CORP, "ds001-simwon.json");
    expectGet(ds001Server, "corp_code=" + LEGACY_CORP, "ds001-legacycorp.json");
    RestTemplate ds006Template = new RestTemplate();
    MockRestServiceServer ds006Server =
        MockRestServiceServer.bindTo(ds006Template).ignoreExpectOrder(true).build();
    expectGet(ds006Server, "corp_code=" + SIMWON_CORP, "ds006-simwon.json");
    expectGet(ds006Server, "corp_code=" + LEGACY_CORP, "ds006-legacycorp.json");

    IpoSyncService syncService =
        buildRecoverOnlyService(ds001Template, ds001Server, ds006Template, ds006Server);
    syncService.recoverAfterDeploy();

    assertThat(ipoRepository.findById(legacy.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.INACTIVE);
    assertThat(ipoRepository.findById(simwon.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.INACTIVE);
    Optional<IpoSyncRun> recover =
        ipoSyncRunRepository.findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
            IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_SUCCESS);
    assertThat(recover).isPresent();
    assertThat(recover.get().getTotalTargets()).isEqualTo(2);
    assertThat(recover.get().getSucceeded()).isEqualTo(2);
  }

  @Test
  void emptyApiResults_deleteNothing_andFailureRestartRecovers() throws Exception {
    Ipo seeded =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 11, 9));

    // 1) 빈 API 결과: 철회 근거도, 확인 가능한 정상 공시도 없으면 성공이 아니라 미확인 실패다 (행 ACTIVE 유지)
    IpoSyncService emptyService =
        newSyncService(
            "ds001-empty.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-empty.json",
            "corp_code=" + SIMWON_CORP,
            null);
    emptyService.recoverAfterDeploy();
    assertThat(ipoRepository.findById(seeded.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.ACTIVE);
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isZero();
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_FAILED))
        .isEqualTo(1);
    ipoSyncRunRepository.deleteAll();

    // 2) DS006 빈 응답(일정 누락): 철회하지 않고 FAILED만 기록한다
    IpoSyncService failingService =
        newSyncService(
            "ds001-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-empty.json",
            "corp_code=" + SIMWON_CORP,
            null);
    failingService.recoverAfterDeploy();
    assertThat(ipoRepository.findById(seeded.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.ACTIVE);
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isZero();
    Optional<IpoSyncRun> failed =
        ipoSyncRunRepository.findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
            IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_FAILED);
    assertThat(failed).isPresent();
    assertThat(failed.get().getFailures())
        .anySatisfy(f -> assertThat(f.reason()).contains("철회점검:"));

    // 3) 재기동(복구 재시도): 정상 응답이면 SUCCESS로 전이한다
    IpoSyncService recoveringService =
        newSyncService(
            "ds001-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-simwon.json",
            "corp_code=" + SIMWON_CORP,
            null);
    recoveringService.recoverAfterDeploy();
    assertThat(ipoRepository.findById(seeded.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.INACTIVE);
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(1);
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_FAILED))
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void duplicateActiveRows_areNotOverwrittenAndHeldBack() throws Exception {
    Ipo first =
        seedAutoIpo(
            DUPLICATE_CORP,
            "복수공모가",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            null,
            null,
            null,
            null);
    Ipo second =
        seedAutoIpo(
            DUPLICATE_CORP,
            "복수공모나",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            null,
            null,
            null,
            null);

    IpoSyncService syncService =
        newSyncService(
            "ds001-duplicate.json",
            "corp_code=" + DUPLICATE_CORP,
            "ds006-duplicate.json",
            "corp_code=" + DUPLICATE_CORP,
            null);
    syncService.recoverAfterDeploy();

    assertThat(ipoRepository.findById(first.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.ACTIVE);
    assertThat(ipoRepository.findById(second.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.ACTIVE);
    Optional<IpoSyncRun> failed =
        ipoSyncRunRepository.findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
            IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_FAILED);
    assertThat(failed).isPresent();
    assertThat(failed.get().getFailures()).anySatisfy(f -> assertThat(f.reason()).contains("복수"));
  }

  @Test
  void trackedReceiptMismatch_sameSubscriptionDate_isHeldBack() throws Exception {
    Ipo seeded =
        seedAutoIpo(
            MISMATCH_CORP,
            "미스매치",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            "20260202000002",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 9));

    IpoSyncService syncService =
        newSyncService(
            "ds001-mismatch.json",
            "corp_code=" + MISMATCH_CORP,
            "ds006-mismatch.json",
            "corp_code=" + MISMATCH_CORP,
            null);
    syncService.recoverAfterDeploy();

    assertThat(ipoRepository.findById(seeded.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.ACTIVE);
    Optional<IpoSyncRun> failed =
        ipoSyncRunRepository.findTopByRunTypeAndAppVersionAndStatusOrderByIdDesc(
            IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.RECOVERY_KEY, IpoSyncRun.STATUS_FAILED);
    assertThat(failed).isPresent();
    assertThat(failed.get().getFailures())
        .anySatisfy(f -> assertThat(f.reason()).contains("구분 불가"));
  }

  @Test
  void trackedRow_survivesReSyncWithSameReceipt_andUpdatesOnChangedDate() throws Exception {
    Ipo seeded =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            "20261012000149",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 9));

    OpenDartEquitySecurityDetail changed =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .corpCode(SIMWON_CORP)
            .corpName("심원")
            .sbd("2026년 12월 05일 ~ 2026년 12월 06일")
            .stkcnt("1,500,000")
            .slprc("15,000")
            .pymd("2026년 12월 09일")
            .build();
    changed.parseDerivedFields();

    transactionTemplate.executeWithoutResult(
        status -> ipoSyncPersistence.persist(SIMWON_CORP, "심원", changed, false, false, null));

    // 추적값 == 현재값이면 수집 원본 정정은 허용한다
    Ipo reloaded = ipoRepository.findById(seeded.getId()).orElseThrow();
    assertThat(reloaded.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 12, 5));
    assertThat(reloaded.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 12, 6));
    assertThat(reloaded.getRefundDate()).isEqualTo(LocalDate.of(2026, 12, 9));
    assertThat(reloaded.getSourceReceiptNo()).isEqualTo("20261012000149");
    assertThat(reloaded.getLastImportedSubscriptionStartDate())
        .isEqualTo(LocalDate.of(2026, 12, 5));
  }

  @Test
  void trackedNullDates_areProtectedFromAutoSync() {
    Ipo seeded =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            LocalDate.of(2026, 11, 9),
            null,
            null,
            null,
            null);

    OpenDartEquitySecurityDetail changed =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .corpCode(SIMWON_CORP)
            .corpName("심원")
            .sbd("2026년 12월 05일 ~ 2026년 12월 06일")
            .stkcnt("1,500,000")
            .slprc("15,000")
            .pymd("2026년 12월 09일")
            .build();
    changed.parseDerivedFields();

    transactionTemplate.executeWithoutResult(
        status -> ipoSyncPersistence.persist(SIMWON_CORP, "심원", changed, false, false, null));

    // 추적값이 null인 행(출처 불명/수동)은 자동 동기화가 덮어쓰지 않는다
    Ipo reloaded = ipoRepository.findById(seeded.getId()).orElseThrow();
    assertThat(reloaded.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(reloaded.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    assertThat(reloaded.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 9));
  }

  @Test
  void adminUpdatedSubscriptionDates_areProtected() {
    Ipo seeded =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            "20261012000149",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 9));

    IpoUpdateRequest request = new IpoUpdateRequest();
    request.setSubscriptionStartDate(LocalDate.of(2026, 11, 20));
    request.setSubscriptionEndDate(LocalDate.of(2026, 11, 21));
    ipoService.update(seeded.getId(), request);

    Ipo reloaded = ipoRepository.findById(seeded.getId()).orElseThrow();
    assertThat(reloaded.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(reloaded.getLastImportedSubscriptionStartDate()).isNull();
    assertThat(reloaded.getLastImportedSubscriptionEndDate()).isNull();

    OpenDartEquitySecurityDetail changed =
        OpenDartEquitySecurityDetail.builder()
            .rceptNo("20261012000149")
            .corpCode(SIMWON_CORP)
            .corpName("심원")
            .sbd("2027년 01월 05일 ~ 2027년 01월 06일")
            .stkcnt("1,500,000")
            .slprc("15,000")
            .pymd("2027년 01월 09일")
            .build();
    changed.parseDerivedFields();
    transactionTemplate.executeWithoutResult(
        status -> ipoSyncPersistence.persist(SIMWON_CORP, "심원", changed, false, false, null));

    Ipo afterSync = ipoRepository.findById(seeded.getId()).orElseThrow();
    assertThat(afterSync.getSubscriptionStartDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(afterSync.getSubscriptionEndDate()).isEqualTo(LocalDate.of(2026, 11, 21));
  }

  @Test
  void twoConsecutiveDailySuccesses_leaveTwoDailyRowsAndOneRecoverRow() throws Exception {
    seedAutoIpo(
        SIMWON_CORP,
        "심원",
        LocalDate.of(2026, 11, 5),
        LocalDate.of(2026, 11, 6),
        null,
        null,
        null,
        null,
        LocalDate.of(2026, 11, 9));

    // 1차 실행(철회 처리)과 2차 실행(대상 0건, 전수 성공)이 각각 DAILY SUCCESS를 남겨야 한다
    Clock dayOne =
        Clock.fixed(LocalDateTime.of(2026, 11, 10, 4, 30).atZone(SEOUL).toInstant(), SEOUL);
    Clock dayTwo =
        Clock.fixed(LocalDateTime.of(2026, 11, 11, 4, 30).atZone(SEOUL).toInstant(), SEOUL);

    IpoSyncService first =
        newSyncServiceWithClock(
            "ds001-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds001-simwon.json",
            dayOne);
    IpoSyncResult firstResult = first.sync(90);
    IpoSyncService second =
        newSyncServiceWithClock(
            "ds001-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds001-simwon.json",
            dayTwo);
    IpoSyncResult secondResult = second.sync(90);

    assertThat(firstResult.getFailureCount())
        .as("first day failures: %s",
            firstResult.getFailures().stream().map(IpoSyncFailure::getReason).toList())
        .isZero();
    assertThat(secondResult.getFailureCount())
        .as("second day failures: %s",
            secondResult.getFailures().stream().map(IpoSyncFailure::getReason).toList())
        .isZero();

    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_DAILY_RECONCILE, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(2);
    assertThat(
            ipoSyncRunRepository.countByRunTypeAndStatus(
                IpoSyncRun.RUN_POST_DEPLOY_RECOVER, IpoSyncRun.STATUS_SUCCESS))
        .isEqualTo(1);
  }

  @Test
  void withdrawnOffering_keepsInvestmentHistoryRows() throws Exception {
    Ipo seeded =
        seedAutoIpo(
            SIMWON_CORP,
            "심원",
            LocalDate.of(2026, 11, 5),
            LocalDate.of(2026, 11, 6),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 11, 9));

    LocalDateTime now = LocalDateTime.now(clock);
    Account account =
        accountRepository.saveAndFlush(
            Account.builder()
                .email("history-" + ThreadLocalRandom.current().nextLong() + "@example.com")
                .passwordHash("encodedHash")
                .name("이력보존테스터")
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAccountIds.add(account.getId());
    Investor investor =
        investorRepository.saveAndFlush(
            Investor.builder()
                .accountId(account.getId())
                .displayName("본인")
                .relationship("SELF")
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdInvestorIds.add(investor.getId());

    Subscription subscription =
        subscriptionRepository.saveAndFlush(
            Subscription.builder()
                .investorId(investor.getId())
                .securitiesAccountId(1L)
                .ipoId(seeded.getId())
                .quantity(100L)
                .depositAmount(1_500_000L)
                .subscribedAt(now.minusDays(10))
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdSubscriptionIds.add(subscription.getId());
    Allocation allocation =
        allocationRepository.saveAndFlush(
            Allocation.builder()
                .subscriptionId(subscription.getId())
                .allocatedQuantity(30L)
                .allocatedAt(now.minusDays(8))
                .allocatedAmount(450_000L)
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdAllocationIds.add(allocation.getId());
    Sale sale =
        saleRepository.saveAndFlush(
            Sale.builder()
                .allocationId(allocation.getId())
                .quantity(30L)
                .amount(900_000L)
                .fee(1_000L)
                .tax(500L)
                .saleDate(now.minusDays(1))
                .status(RecordStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
    createdSaleIds.add(sale.getId());

    IpoSyncService recoverService =
        newSyncService(
            "ds001-simwon.json",
            "corp_code=" + SIMWON_CORP,
            "ds006-simwon.json",
            "corp_code=" + SIMWON_CORP,
            null);
    recoverService.recoverAfterDeploy();

    assertThat(ipoRepository.findById(seeded.getId()).orElseThrow().getStatus())
        .isEqualTo(RecordStatus.INACTIVE);
    // 내역조회·수익계산이 참조하는 투자 이력 행은 그대로 유지되고 ipoId 참조도 보존된다
    assertThat(subscriptionRepository.findById(subscription.getId())).isPresent();
    Allocation reloadedAllocation = allocationRepository.findById(allocation.getId()).orElseThrow();
    assertThat(reloadedAllocation.getSubscriptionId()).isEqualTo(subscription.getId());
    Sale reloadedSale = saleRepository.findById(sale.getId()).orElseThrow();
    assertThat(reloadedSale.getAllocationId()).isEqualTo(allocation.getId());
    assertThat(subscriptionRepository.findById(subscription.getId()).orElseThrow().getIpoId())
        .isEqualTo(seeded.getId());
  }

  private int countActiveIpos(String corpCode) {
    return (int)
        ipoRepository.findAll().stream()
            .filter(ipo -> corpCode.equals(ipo.getCorpCode()))
            .filter(ipo -> ipo.getStatus() == RecordStatus.ACTIVE)
            .filter(ipo -> ipo.getDeletedAt() == null)
            .count();
  }

  private IpoSyncService newSyncServiceWithClock(
      String ds001FullCorpFixture,
      String ds001FullCorpMatcher,
      String ds006Fixture,
      String ds006Matcher,
      String ds001RecentFixture,
      Clock fixedClock)
      throws IOException {
    RestTemplate ds001Template = new RestTemplate();
    MockRestServiceServer ds001Server =
        MockRestServiceServer.bindTo(ds001Template).ignoreExpectOrder(true).build();
    RestClient ds001Client =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(ds001Template.getRequestFactory())
            .build();
    if (ds001FullCorpFixture != null) {
      expectGet(ds001Server, ds001FullCorpMatcher, ds001FullCorpFixture);
      // 철회 후 최신 정정의 원공시 확인을 위해 회사별 이력을 추가 조회한다.
      if ("ds001-simwon.json".equals(ds001FullCorpFixture) && ds001RecentFixture != null) {
        expectGet(ds001Server, ds001FullCorpMatcher, ds001FullCorpFixture);
      }
    }
    if (ds001RecentFixture != null) {
      expectGet(ds001Server, not(containsString("corp_code=")), ds001RecentFixture);
    }

    RestTemplate ds006Template = new RestTemplate();
    MockRestServiceServer ds006Server =
        MockRestServiceServer.bindTo(ds006Template).ignoreExpectOrder(true).build();
    RestClient ds006Client =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(ds006Template.getRequestFactory())
            .build();
    for (int i = 0; i < 2; i++) {
      expectGet(ds006Server, ds006Matcher, ds006Fixture);
    }

    OpenDartApiProperties properties =
        new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr");
    var throttle = new com.ipocalc.ipocatalog.service.support.HttpClientSupport.RequestThrottle(0);
    OpenDartCorpDisclosureClient corpClient =
        new OpenDartCorpDisclosureClient(ds001Client, properties, fixedClock, throttle);
    OpenDartEquitySecuritiesClient equityClient =
        new OpenDartEquitySecuritiesClient(ds006Client, properties, throttle);

    UstockPlusCalendarClient ustockplus = mock(UstockPlusCalendarClient.class);
    when(ustockplus.fetchScheduleDates(any(), any()))
        .thenReturn(List.<UstockPlusScheduleDates>of());
    IpoScheduleMergeService mergeService =
        new IpoScheduleMergeService(ustockplus, ipoRepository, transactionTemplate, fixedClock);
    IpoReconciliationService reconciliationService =
        new IpoReconciliationService(
            corpClient,
            equityClient,
            mock(DartIpoFilingClient.class),
            mock(DartOfferingTermsParser.class),
            ipoRepository,
            ipoSyncPersistence,
            transactionTemplate,
            fixedClock);
    return new IpoSyncService(
        corpClient,
        equityClient,
        filingClientWithSimwonOriginalDocument(),
        new DartOfferingTermsParser(),
        ipoSyncPersistence,
        transactionTemplate,
        mergeService,
        reconciliationService,
        new IpoSyncRunRecorder(ipoSyncRunRepository, fixedClock, transactionTemplate),
        fixedClock);
  }

  private IpoSyncService buildRecoverOnlyService(
      RestTemplate ds001Template,
      MockRestServiceServer ds001Server,
      RestTemplate ds006Template,
      MockRestServiceServer ds006Server) {
    RestClient ds001Client =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(ds001Template.getRequestFactory())
            .build();
    RestClient ds006Client =
        RestClient.builder()
            .baseUrl("https://opendart.fss.or.kr")
            .requestFactory(ds006Template.getRequestFactory())
            .build();
    OpenDartApiProperties properties =
        new OpenDartApiProperties("test-api-key", "https://opendart.fss.or.kr");
    var throttle = new com.ipocalc.ipocatalog.service.support.HttpClientSupport.RequestThrottle(0);
    OpenDartCorpDisclosureClient corpClient =
        new OpenDartCorpDisclosureClient(ds001Client, properties, clock, throttle);
    OpenDartEquitySecuritiesClient equityClient =
        new OpenDartEquitySecuritiesClient(ds006Client, properties, throttle);
    UstockPlusCalendarClient ustockplus = mock(UstockPlusCalendarClient.class);
    when(ustockplus.fetchScheduleDates(any(), any()))
        .thenReturn(List.<UstockPlusScheduleDates>of());
    IpoScheduleMergeService mergeService =
        new IpoScheduleMergeService(ustockplus, ipoRepository, transactionTemplate, clock);
    IpoReconciliationService reconciliationService =
        new IpoReconciliationService(
            corpClient,
            equityClient,
            mock(DartIpoFilingClient.class),
            mock(DartOfferingTermsParser.class),
            ipoRepository,
            ipoSyncPersistence,
            transactionTemplate,
            clock);
    return new IpoSyncService(
        corpClient,
        equityClient,
        mock(DartIpoFilingClient.class),
        mock(DartOfferingTermsParser.class),
        ipoSyncPersistence,
        transactionTemplate,
        mergeService,
        reconciliationService,
        new IpoSyncRunRecorder(ipoSyncRunRepository, clock, transactionTemplate),
        clock);
  }
}
