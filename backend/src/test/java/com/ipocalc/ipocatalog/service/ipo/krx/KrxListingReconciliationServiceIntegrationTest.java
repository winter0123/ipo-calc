package com.ipocalc.ipocatalog.service.ipo.krx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.controller.ipo.dto.KrxListingApplyRequest;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.DartRightsIssueListingService;
import com.ipocalc.ipocatalog.service.ipo.sync.opendart.DartIpoFilingClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
class KrxListingReconciliationServiceIntegrationTest {

  private static final String KOIZ_URL =
      "https://kind.krx.co.kr/external/2026/02/05/001019/20260205002638/70791.htm";
  private static final String AMICO_URL =
      "https://kind.krx.co.kr/external/2026/02/23/000883/20260223002329/70791.htm";
  private static final String RAON_URL =
      "https://kind.krx.co.kr/external/2026/03/05/001391/20260305003200/70791.htm";
  private static final String VIVO_URL =
      "https://kind.krx.co.kr/external/2026/03/16/001505/20260316004580/70791.htm";
  private static final String RIGHTS_SOURCE_RECEIPT = "20251105000215";
  private static final String RIGHTS_ISSUANCE_RECEIPT = "20260211000001";
  private static final String RIGHTS_ISSUANCE_XML =
      "<DOCUMENT><P>원신고서 20251105000215</P><TABLE><TR><TD>납입일</TD><TD>2026.01.29</TD></TR>"
          + "<TR><TD>신주 상장 및 유통개시 예정일</TD><TD>2026.02.11</TD></TR></TABLE></DOCUMENT>";

  private static final CaseData KOIZ =
      new CaseData(
          "koiz.html",
          "20260129000503",
          "121850",
          LocalDate.of(2026, 2, 10),
          LocalDate.of(2026, 1, 20),
          KOIZ_URL);
  private static final CaseData AMICO =
      new CaseData(
          "amico.html",
          "20260211000586",
          "092040",
          LocalDate.of(2026, 2, 26),
          LocalDate.of(2026, 2, 10),
          AMICO_URL);
  private static final CaseData RAON =
      new CaseData(
          "raon.html",
          "20260225003924",
          "300120",
          LocalDate.of(2026, 3, 10),
          LocalDate.of(2026, 2, 20),
          RAON_URL);
  private static final CaseData VIVO =
      new CaseData(
          "vivo.html",
          "20260304000919",
          "082800",
          LocalDate.of(2026, 3, 19),
          LocalDate.of(2026, 3, 10),
          VIVO_URL);
  private static final CaseData RAON_OTHER_ROUND =
      new CaseData(
          "raon-other-round.html",
          "20260225003924",
          "300120",
          LocalDate.of(2026, 3, 10),
          LocalDate.of(2026, 2, 20),
          RAON_URL);

  private static final CaseData[] CASES = {KOIZ, AMICO, RAON, VIVO};

  @Autowired private IpoRepository ipoRepository;
  @Autowired private KrxListingReconciliationService service;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DartRightsIssueListingService dartRightsIssueListingService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @PersistenceContext private EntityManager entityManager;

  @MockitoBean private KrxListingNoticeClient noticeClient;
  @MockitoBean private DartIpoFilingClient dartClient;

  private final List<Long> createdIds = new ArrayList<>();

  @AfterEach
  void tearDown() {
    if (!createdIds.isEmpty()) {
      ipoRepository.deleteAllById(createdIds);
      ipoRepository.flush();
    }
    createdIds.clear();
  }

  // 70760 신규상장 원문 공식 필드에 근거한 합성 HTML이며 실원문 파일 재생은 아니다.
  private static final String NEW_LISTING_URL =
      "https://kind.krx.co.kr/external/2026/11/10/000840/20261110001806/70760.htm";
  private static final String NEW_LISTING_HTML =
      "<html><body>신규상장"
          + "<table><tr><td>2.주식의 종류와 수</td><td>주권 종류</td>"
          + "<td>표준코드</td><td>단축코드</td><td>주식수(주)</td></tr>"
          + "<tr><td>보통주</td><td>KR7493410005</td>"
          + "<td>A493410</td><td>45,845,239</td></tr>"
          + "<tr><td>3.액면가(원)</td><td>500</td></tr>"
          + "<tr><td>10.상장일(매매개시일)</td>"
          + "<td>2026년 11월 12일</td></tr></table></body></html>";

  // 공식 필드를 재구성한 합성 응답: 검색 → 접수번호 → 뷰어 → 원문 → 실제 PostgreSQL CAS.
  private KrxListingAutoSyncService syntheticSearchToRealDatabase(boolean failSearch) {
    var discovery =
        new KrxListingNoticeDiscoveryClient(
            (path, params, post) -> {
              if ("/disclosure/details.do".equals(path)) {
                assertThat(params).containsEntry("repIsuSrtCd", "A493410");
                if (failSearch) {
                  throw new IllegalStateException("KIND 조회 HTTP 403");
                }
                return "<html><table class='list type-00 mt10'><tbody>"
                    + "<tr><td>1</td><td>09:00</td>"
                    + "<td><a onclick=\"companysummary_open('49341')\">넥스아이</a></td>"
                    + "<td><a onclick=\"openDisclsViewer('20261110001806','')\">신규상장</a></td>"
                    + "<td>거래소</td></tr></tbody></table></html>";
              }
              if ("search".equals(params.get("method"))) {
                assertThat(params).containsEntry("acptno", "20261110001806");
                return "<option value='20261110001806|Y'>신규상장</option>";
              }
              assertThat(params).containsEntry("docNo", "20261110001806");
              return NEW_LISTING_URL;
            });
    var automatic =
        new KrxListingAutoSyncService(
            discovery,
            service,
            ipoRepository,
            Clock.fixed(Instant.parse("2026-11-22T09:00:00Z"), ZoneId.of("Asia/Seoul")));
    ReflectionTestUtils.setField(automatic, "autoApplyEnabled", true);
    return automatic;
  }

  @Test
  void syntheticSearchToViewerToOfficialHtmlPersistsViaRealPostgresCas() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var automatic = syntheticSearchToRealDatabase(false);

    var first =
        automatic.reconcileSubscriptionMonths(
            YearMonth.of(2026, 11), YearMonth.of(2026, 11), false);

    assertThat(first.counts().get(KrxListingAutoSyncService.OutcomeType.NEW_CONFIRMED))
        .isEqualTo(1);
    assertThat(first.rows()).hasSize(1);
    assertThat(first.rows().get(0).receiptNo()).isEqualTo("20261110001806");
    Ipo confirmed = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(confirmed.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(confirmed.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
    assertThat(confirmed.getKrxListingNoticeUrl()).isEqualTo(NEW_LISTING_URL);

    var again =
        automatic.reconcileSubscriptionMonths(
            YearMonth.of(2026, 11), YearMonth.of(2026, 11), false);
    assertThat(again.counts().get(KrxListingAutoSyncService.OutcomeType.ALREADY_APPLIED))
        .isEqualTo(1);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 12));
  }

  @Test
  void searchHttp403FailsClosedBeforeViewerOrDatabaseWrite() {
    Ipo saved = createNewListingIpo(true);
    var automatic = syntheticSearchToRealDatabase(true);

    var report =
        automatic.reconcileSubscriptionMonths(
            YearMonth.of(2026, 11), YearMonth.of(2026, 11), false);

    assertThat(report.counts().get(KrxListingAutoSyncService.OutcomeType.FETCH_FAILED))
        .isEqualTo(1);
    assertThat(report.counts().get(KrxListingAutoSyncService.OutcomeType.NO_NOTICE)).isZero();
    Ipo unchanged = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(unchanged.getListingDate()).isNull();
    assertThat(unchanged.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void newListingOnlyFillsMissingAutoIpoAndIsIdempotent() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    assertThat(preview.idempotent()).isFalse();
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate()).isNull();

    var result =
        service.applyNewListing(
            saved.getId(),
            NEW_LISTING_URL,
            preview.listingDate(),
            preview.noticeHash(),
            "20261101000001");

    assertThat(result.status()).isEqualTo("APPLIED");
    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(after.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
    assertThat(after.getKrxListingNoticeUrl()).isEqualTo(NEW_LISTING_URL);
    assertThat(after.getSourceReceiptNo()).isEqualTo("20261101000001");
    assertThat(after.getListingDateEvidenceReceipt()).isNull();
    assertThat(after.getLastImportedListingDate()).isEqualTo(after.getListingDate());
    assertThat(
            service
                .applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001")
                .status())
        .isEqualTo("NO_OP");
    transactionTemplate.executeWithoutResult(
        status -> {
          Ipo current = ipoRepository.findById(saved.getId()).orElseThrow();
          current.mergeSupplementalSchedule(
              "493410",
              null,
              null,
              LocalDate.of(2026, 11, 14),
              "OPENDART+USTOCKPLUS",
              LocalDateTime.now());
          assertThat(
                  current.fillMissingRightsListing(
                      LocalDate.of(2026, 11, 15), "20261113000001", LocalDateTime.now()))
              .isFalse();
        });
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 12));
  }

  @Test
  void importedSupplementalDateIsPromotedToOfficialDateAndRetryIsNoOp() {
    Ipo saved = createNewListingIpo(true);
    transactionTemplate.executeWithoutResult(
        status -> {
          Ipo current = ipoRepository.findById(saved.getId()).orElseThrow();
          assertThat(
                  current.mergeSupplementalSchedule(
                      null,
                      null,
                      null,
                      LocalDate.of(2026, 11, 14),
                      "OPENDART+USTOCKPLUS",
                      LocalDateTime.now()))
              .isTrue();
        });
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);

    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getLastImportedListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 14));

    assertThat(
            service
                .applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001")
                .status())
        .isEqualTo("APPLIED");
    Ipo confirmed = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(confirmed.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(confirmed.getLastImportedListingDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(confirmed.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
    assertThat(
            service
                .applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001")
                .status())
        .isEqualTo("NO_OP");
  }

  @Test
  void unknownDateWithoutImportedTrackingCannotBePromoted() {
    Ipo unknown = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    transactionTemplate.executeWithoutResult(
        status -> {
          entityManager
              .createNativeQuery("update ipos set listing_date = ?1 where id = ?2")
              .setParameter(1, java.sql.Date.valueOf(LocalDate.of(2026, 11, 14)))
              .setParameter(2, unknown.getId())
              .executeUpdate();
        });
    entityManager.clear();

    var preview = service.previewNewListing(unknown.getId(), NEW_LISTING_URL);

    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("수동 상장일"));
    assertThat(ipoRepository.findById(unknown.getId()).orElseThrow().getListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 14));
  }

  @Test
  void manuallyEditedImportedDateCannotBePromoted() {
    Ipo edited = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    transactionTemplate.executeWithoutResult(
        status -> {
          Ipo tracked = ipoRepository.findById(edited.getId()).orElseThrow();
          tracked.mergeSupplementalSchedule(
              null,
              null,
              null,
              LocalDate.of(2026, 11, 14),
              "OPENDART+USTOCKPLUS",
              LocalDateTime.now());
        });
    transactionTemplate.executeWithoutResult(
        status -> {
          entityManager
              .createNativeQuery("update ipos set listing_date = ?1 where id = ?2")
              .setParameter(1, java.sql.Date.valueOf(LocalDate.of(2026, 11, 13)))
              .setParameter(2, edited.getId())
              .executeUpdate();
        });
    entityManager.clear();

    var preview = service.previewNewListing(edited.getId(), NEW_LISTING_URL);

    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("수동 상장일"));
    assertThat(ipoRepository.findById(edited.getId()).orElseThrow().getListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 13));
  }

  @Test
  void importedDateAlsoRejectsChangedSubscriptionEndAfterPreview() {
    Ipo saved = createNewListingIpo(true);
    transactionTemplate.executeWithoutResult(
        status -> {
          Ipo current = ipoRepository.findById(saved.getId()).orElseThrow();
          current.mergeSupplementalSchedule(
              null,
              null,
              null,
              LocalDate.of(2026, 11, 14),
              "OPENDART+USTOCKPLUS",
              LocalDateTime.now());
        });
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery("update ipos set subscription_end_date = ?1 where id = ?2")
                .setParameter(1, java.sql.Date.valueOf(LocalDate.of(2026, 11, 15)))
                .setParameter(2, saved.getId())
                .executeUpdate());
    entityManager.clear();

    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    Ipo unchanged = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(unchanged.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 14));
    assertThat(unchanged.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void withdrawnAndDeletedIposCannotBeConfirmedByNewListing() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    transactionTemplate.executeWithoutResult(
        status ->
            ipoRepository
                .findById(saved.getId())
                .orElseThrow()
                .withdraw("20261112000001", LocalDateTime.now()));

    var withdrawn = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(withdrawn.applyAllowed()).isFalse();
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate()).isNull();
  }

  @Test
  void differentConfirmedKindNoticeIsNeverReplacedByNewListing() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    String otherUrl = "https://kind.krx.co.kr/external/2026/11/11/000999/20261111001234/70760.htm";
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery(
                    "update ipos set listing_date = ?1, "
                        + "listing_date_evidence_type = 'CONFIRMED_KRX', "
                        + "krx_listing_notice_url = ?2 where id = ?3")
                .setParameter(1, java.sql.Date.valueOf(LocalDate.of(2026, 11, 12)))
                .setParameter(2, otherUrl)
                .setParameter(3, saved.getId())
                .executeUpdate());
    entityManager.clear();

    var changed = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(changed.applyAllowed()).isFalse();
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getKrxListingNoticeUrl())
        .isEqualTo(otherUrl);
  }

  @Test
  void subscriptionEndChangeAfterPreviewMustConflictWithoutWrite() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    transactionTemplate.executeWithoutResult(
        status -> {
          entityManager
              .createNativeQuery("update ipos set subscription_end_date = ?1 where id = ?2")
              .setParameter(1, java.sql.Date.valueOf(LocalDate.of(2026, 11, 15)))
              .setParameter(2, saved.getId())
              .executeUpdate();
        });
    entityManager.clear();

    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    Ipo unchanged = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(unchanged.getListingDate()).isNull();
    assertThat(unchanged.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void newListingCannotOverrideManualIpoOrWrongStockCode() {
    Ipo manual = createNewListingIpo(false);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var manualPreview = service.previewNewListing(manual.getId(), NEW_LISTING_URL);
    assertThat(manualPreview.applyAllowed()).isFalse();
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    manual.getId(),
                    NEW_LISTING_URL,
                    manualPreview.listingDate(),
                    manualPreview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(manual.getId()).orElseThrow().getListingDate()).isNull();
  }

  @Test
  void newListingRejectsWrongStockCode() {
    Ipo different = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL))
        .thenReturn(NEW_LISTING_HTML.replace("A493410", "A493411"));
    var wrongCode = service.previewNewListing(different.getId(), NEW_LISTING_URL);
    assertThat(wrongCode.applyAllowed()).isFalse();
    assertThat(wrongCode.reasons()).anyMatch(reason -> reason.contains("종목코드"));
  }

  @Test
  void newListingRejectsStaleDocumentAndChangedOriginalReceipt() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    assertThat(preview.applyAllowed()).isTrue();
    when(noticeClient.fetchNewListing(NEW_LISTING_URL))
        .thenReturn(NEW_LISTING_HTML.replace("2026년 11월 12일", "2026년 11월 13일"));
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    transactionTemplate.executeWithoutResult(
        status -> {
          ipoRepository
              .findById(saved.getId())
              .orElseThrow()
              .recordSyncSourceReceipt("20261102000001");
        });
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate()).isNull();
  }

  @Test
  void newListingCannotOverwriteManualDateSetAfterPreview() {
    Ipo saved = createNewListingIpo(true);
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);
    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery("update ipos set listing_date = ?1 where id = ?2")
                .setParameter(1, java.sql.Date.valueOf(LocalDate.of(2026, 11, 15)))
                .setParameter(2, saved.getId())
                .executeUpdate());
    entityManager.clear();
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate())
        .isEqualTo(LocalDate.of(2026, 11, 15));
  }

  @Test
  void newListingCannotOverwriteManuallyRecordedOpenPrice() {
    Ipo saved = createNewListingIpo(true);
    transactionTemplate.executeWithoutResult(
        status ->
            ipoRepository.findById(saved.getId()).orElseThrow().updateListingOpenPrice(13_000L));
    when(noticeClient.fetchNewListing(NEW_LISTING_URL)).thenReturn(NEW_LISTING_HTML);

    var preview = service.previewNewListing(saved.getId(), NEW_LISTING_URL);

    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("덮어쓸 수 없습니다"));
    assertThatThrownBy(
            () ->
                service.applyNewListing(
                    saved.getId(),
                    NEW_LISTING_URL,
                    preview.listingDate(),
                    preview.noticeHash(),
                    "20261101000001"))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingOpenPrice())
        .isEqualTo(13_000L);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate()).isNull();
  }

  private Ipo createNewListingIpo(boolean auto) {
    Ipo result =
        ipoRepository.saveAndFlush(
            Ipo.builder()
                .companyName("넥스아이")
                .ipoName("넥스아이")
                .stockCode("493410")
                .subscriptionStartDate(LocalDate.of(2026, 11, 3))
                .subscriptionEndDate(LocalDate.of(2026, 11, 6))
                .status(RecordStatus.ACTIVE)
                .syncSource(auto ? "OPENDART" : null)
                .sourceReceiptNo(auto ? "20261101000001" : null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    createdIds.add(result.getId());
    return result;
  }

  @Test
  void appliesFourSyntheticRoundLinkedCasesAndIsIdempotent() throws Exception {
    for (CaseData c : CASES) {
      stubLinkedNotice(c);
      Ipo saved = createIpo(c);

      var preview = service.preview(saved.getId(), c.url());
      assertThat(preview.applyAllowed()).isTrue();
      assertThat(preview.expectedNoticeHash()).isNotBlank();

      Ipo before = ipoRepository.findById(saved.getId()).orElseThrow();
      LocalDate beforeListingDate = before.getListingDate();

      var result = service.apply(saved.getId(), request(preview, c.url()));
      assertThat(result.status()).isEqualTo("APPLIED");

      Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
      assertThat(after.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
      assertThat(after.getListingDate()).isEqualTo(c.listingDate());
      assertThat(after.getKrxListingNoticeUrl()).isEqualTo(c.url());
      assertThat(after.getListingDateEvidenceReceipt()).isEqualTo(c.dartReceipt());
      assertThat(after.getLastImportedListingDate()).isEqualTo(beforeListingDate);

      // 같은 적용 재시도는 정확히 no-op으로 보고한다.
      var retry = service.apply(saved.getId(), request(preview, c.url()));
      assertThat(retry.status()).isEqualTo("NO_OP");
      Ipo afterRetry = ipoRepository.findById(saved.getId()).orElseThrow();
      assertThat(afterRetry.getListingDate()).isEqualTo(c.listingDate());
      assertThat(afterRetry.getKrxListingNoticeUrl()).isEqualTo(c.url());
    }
  }

  @Test
  void previewDoesNotWriteAndExposesExpectedValues() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    Ipo before = ipoRepository.findById(saved.getId()).orElseThrow();
    LocalDateTime beforeUpdatedAt = before.getUpdatedAt();

    var preview = service.preview(saved.getId(), KOIZ.url());

    assertThat(preview.applyAllowed()).isTrue();
    assertThat(preview.expectedDartReceiptNo()).isEqualTo(KOIZ.dartReceipt());
    assertThat(preview.expectedDartListingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(preview.expectedStockCode()).isEqualTo(KOIZ.stockCode());
    assertThat(preview.expectedKrxListingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(preview.expectedNoticeHash()).isNotBlank();

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(after.getListingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(after.getListingDateEvidenceReceipt()).isEqualTo(KOIZ.dartReceipt());
    assertThat(after.getKrxListingNoticeUrl()).isNull();
    assertThat(after.getUpdatedAt()).isEqualTo(beforeUpdatedAt);
  }

  @Test
  void krxOfficialDateCanCorrectDifferentDartPlannedDate() throws Exception {
    stubLinkedNotice(KOIZ);
    String url = KOIZ.url();
    String changedHtml =
        readResource("/krx/notices/" + KOIZ.file()).replace("2026-02-10", "2026-02-12");
    when(noticeClient.fetch(url)).thenReturn(changedHtml);
    Ipo saved = createIpo(KOIZ);

    var preview = service.preview(saved.getId(), url);

    assertThat(preview.expectedDartListingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(preview.expectedKrxListingDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(preview.applyAllowed()).isTrue();
    assertThat(preview.reasons()).isEmpty();
    var result = service.apply(saved.getId(), request(preview, url));
    assertThat(result.status()).isEqualTo("APPLIED");
    var confirmed = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(confirmed.getListingDate()).isEqualTo(LocalDate.of(2026, 2, 12));
    assertThat(confirmed.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
    assertThat(confirmed.getLastImportedListingDate()).isEqualTo(KOIZ.listingDate());
  }

  @Test
  void futureOfficialListingCanBePublishedBeforeTradingDay() throws Exception {
    stubLinkedNotice(KOIZ);
    String url = KOIZ.url();
    String changedHtml =
        readResource("/krx/notices/" + KOIZ.file()).replace("2026-02-10", "2027-03-11");
    when(noticeClient.fetch(url)).thenReturn(changedHtml);
    Ipo saved = createIpo(KOIZ);

    var preview = service.preview(saved.getId(), url);

    assertThat(preview.expired()).isFalse();
    assertThat(preview.applyAllowed()).isTrue();
    assertThat(preview.expectedKrxListingDate()).isEqualTo(LocalDate.of(2027, 3, 11));
    assertThat(service.apply(saved.getId(), request(preview, url)).status()).isEqualTo("APPLIED");
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDate())
        .isEqualTo(LocalDate.of(2027, 3, 11));
  }

  @Test
  void sameListingDayAndQuantitiesButDifferentIssuanceRoundCannotBeConfirmed() throws Exception {
    stubLinkedNotice(KOIZ);
    String url = KOIZ.url();
    String differentRound =
        readResource("/krx/notices/" + KOIZ.file()).replace("제16회('26.01.30)", "제17회('26.02.15)");
    assertThat(differentRound).isNotEqualTo(readResource("/krx/notices/" + KOIZ.file()));
    when(noticeClient.fetch(url)).thenReturn(differentRound);
    Ipo saved = createIpo(KOIZ);

    var preview = service.preview(saved.getId(), url);

    assertThat(preview.notice().listingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(preview.issuedSharesMatch()).isTrue();
    assertThat(preview.issuePriceMatch()).isTrue();
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).contains("KRX 발행일이 DART 납입일과 같은 발행 회차로 확인되지 않습니다");
    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, url)))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDateEvidenceType())
        .isEqualTo("ESTIMATED_DART");
  }

  @Test
  void stalePreviewDoesNotWrite() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    assertThat(preview.applyAllowed()).isTrue();

    LocalDate changed = KOIZ.listingDate().plusDays(1);
    transactionTemplate.executeWithoutResult(
        s -> {
          Ipo current = ipoRepository.findById(saved.getId()).orElseThrow();
          current.correctSchedule(changed, null, current.getSubscriptionEndDate());
          ipoRepository.saveAndFlush(current);
        });

    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, KOIZ.url())))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isNotEqualTo("CONFIRMED_KRX");
    assertThat(after.getListingDate()).isEqualTo(changed);
    assertThat(after.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void noticeUrlOnlyJsonIsRejectedAndDoesNotWrite() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    assertThat(preview.applyAllowed()).isTrue();

    // URL만 있는 JSON은 기대값이 null로 역직렬화되어 충돌로 거절된다.
    KrxListingApplyRequest urlOnly =
        objectMapper.readValue(
            "{\"noticeUrl\":\"" + KOIZ.url() + "\"}", KrxListingApplyRequest.class);
    assertThat(urlOnly.expectedDartReceiptNo()).isNull();
    assertThat(urlOnly.expectedNoticeHash()).isNull();

    assertThatThrownBy(() -> service.apply(saved.getId(), urlOnly))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(after.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void concurrentChangeAfterPreviewWritesZero() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    assertThat(preview.applyAllowed()).isTrue();

    CountDownLatch previewReached = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(dartClient.downloadDocumentStrict(KOIZ.dartReceipt()))
        .thenAnswer(
            inv -> {
              previewReached.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return linkedDartXml(KOIZ);
            });

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<KrxListingReconciliationService.KrxListingApplyResult> future =
        executor.submit(() -> service.apply(saved.getId(), request(preview, KOIZ.url())));
    try {
      assertThat(previewReached.await(10, TimeUnit.SECONDS)).isTrue();

      LocalDate changed = KOIZ.listingDate().plusDays(1);
      transactionTemplate.executeWithoutResult(
          s -> {
            Ipo current = ipoRepository.findById(saved.getId()).orElseThrow();
            current.correctSchedule(changed, null, current.getSubscriptionEndDate());
            ipoRepository.saveAndFlush(current);
          });
      release.countDown();

      assertThatThrownBy(future::get)
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(ResponseStatusException.class);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isNotEqualTo("CONFIRMED_KRX");
    assertThat(after.getListingDate()).isEqualTo(KOIZ.listingDate().plusDays(1));

    // 조건부 UPDATE 자체도 날짜/종목코드 변경 시 0행임을 직접 확인한다.
    assertThat(
            ipoRepository.confirmKrxListingIfCurrent(
                saved.getId(),
                KOIZ.stockCode(),
                KOIZ.dartReceipt(),
                KOIZ.listingDate(),
                KOIZ.listingDate(),
                KOIZ.url(),
                LocalDateTime.now()))
        .isZero();
  }

  @Test
  void concurrentIdenticalAppliesReportSingleAppliedAndNoOp() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    assertThat(preview.applyAllowed()).isTrue();
    KrxListingApplyRequest applyRequest = request(preview, KOIZ.url());

    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<KrxListingReconciliationService.KrxListingApplyResult> first =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return service.apply(saved.getId(), applyRequest);
              });
      Future<KrxListingReconciliationService.KrxListingApplyResult> second =
          executor.submit(
              () -> {
                start.await(10, TimeUnit.SECONDS);
                return service.apply(saved.getId(), applyRequest);
              });

      List<String> statuses = new ArrayList<>();
      statuses.add(first.get(30, TimeUnit.SECONDS).status());
      statuses.add(second.get(30, TimeUnit.SECONDS).status());
      assertThat(statuses).containsExactlyInAnyOrder("APPLIED", "NO_OP");
    } finally {
      executor.shutdownNow();
    }

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
    assertThat(after.getListingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(after.getKrxListingNoticeUrl()).isEqualTo(KOIZ.url());
    assertThat(after.getListingDateEvidenceReceipt()).isEqualTo(KOIZ.dartReceipt());
    assertThat(after.getLastImportedListingDate()).isEqualTo(KOIZ.listingDate());
  }

  @Test
  void dartSaveAfterKrxConfirmationPreservesConfirmedEvidence() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createRightsIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    when(dartClient.findIssuanceResults(
            org.mockito.ArgumentMatchers.eq("001019"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new DartIpoFilingClient.FilingRef(
                    RIGHTS_ISSUANCE_RECEIPT, "증권발행실적보고서", LocalDate.of(2026, 2, 11))));
    when(dartClient.findEquityFilings(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(inv -> List.of());
    when(dartClient.downloadDocument(RIGHTS_SOURCE_RECEIPT)).thenReturn(Optional.empty());
    when(dartClient.downloadDocument(RIGHTS_ISSUANCE_RECEIPT))
        .thenReturn(Optional.of(RIGHTS_ISSUANCE_XML));

    CountDownLatch kindUpdated = new CountDownLatch(1);
    CountDownLatch commitKind = new CountDownLatch(1);
    AtomicReference<Long> kindBackendPid = new AtomicReference<>();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> kind =
          pool.submit(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        kindBackendPid.set(
                            jdbcTemplate.queryForObject("select pg_backend_pid()", Long.class));
                        service.apply(saved.getId(), request(preview, KOIZ.url()));
                        kindUpdated.countDown();
                        await(commitKind);
                      }));
      assertThat(kindUpdated.await(10, TimeUnit.SECONDS)).isTrue();
      Future<DartRightsIssueListingService.Result> dart =
          pool.submit(
              () ->
                  dartRightsIssueListingService.backfill(
                      LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)));
      awaitBlockedBackend(kindBackendPid.get());
      commitKind.countDown();
      kind.get(10, TimeUnit.SECONDS);
      DartRightsIssueListingService.Result result = dart.get(10, TimeUnit.SECONDS);
      assertThat(result.saved()).isZero();
      assertThat(result.reviewRequired()).isZero();
      assertThat(result.failures()).isZero();
    } finally {
      commitKind.countDown();
      pool.shutdownNow();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    entityManager.clear();
    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("CONFIRMED_KRX");
    assertThat(after.getListingDateEvidenceReceipt()).isEqualTo(KOIZ.dartReceipt());
    assertThat(after.getKrxListingNoticeUrl()).isEqualTo(KOIZ.url());
    assertThat(after.getListingDate()).isEqualTo(LocalDate.of(2026, 2, 10));
  }

  private void awaitBlockedBackend(long blockingPid) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer blocked =
          jdbcTemplate.queryForObject(
              "select count(*) from pg_stat_activity "
                  + "where pid <> ? and wait_event_type = 'Lock' "
                  + "and ? = any(pg_blocking_pids(pid))",
              Integer.class,
              blockingPid,
              blockingPid);
      if (blocked != null && blocked > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("DART 저장 트랜잭션이 PostgreSQL 행 잠금 대기에 진입하지 않음");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("transaction latch timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Test
  void krxPreviewApplyAfterDartSaveRejectsStaleCandidate() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createRightsIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    transactionTemplate.executeWithoutResult(
        s -> {
          Ipo current = ipoRepository.findById(saved.getId()).orElseThrow();
          current.fillMissingRightsListing(
              KOIZ.listingDate().plusDays(1), "20260215000001", LocalDateTime.now());
          ipoRepository.saveAndFlush(current);
        });

    when(dartClient.downloadDocumentStrict("20260215000001")).thenReturn(linkedDartXml(KOIZ));

    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, KOIZ.url())))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    entityManager.clear();
    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDate()).isEqualTo(KOIZ.listingDate().plusDays(1));
    assertThat(after.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(after.getListingDateEvidenceReceipt()).isEqualTo("20260215000001");
    assertThat(after.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void sameSharesPriceAndNearbyIssueDateWithoutIndependentRoundProofNeverWrite() throws Exception {
    stubNotice(RAON_OTHER_ROUND);
    String nearbyRound =
        readResource("/krx/notices/raon-other-round.html")
            .replace("제20회('26.03.02)", "제20회('26.02.26)");
    when(noticeClient.fetch(RAON_OTHER_ROUND.url())).thenReturn(nearbyRound);
    Ipo saved = createIpo(RAON_OTHER_ROUND);

    var preview = service.preview(saved.getId(), RAON_OTHER_ROUND.url());

    assertThat(preview.issuedSharesMatch()).isTrue();
    assertThat(preview.issuePriceMatch()).isTrue();
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("독립적인 연결 근거가 없습니다"));
    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, RAON_OTHER_ROUND.url())))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getKrxListingNoticeUrl())
        .isNull();
  }

  @Test
  void differentExplicitRoundsWithSameSharesPriceAndIssueDayNeverWrite() throws Exception {
    stubLinkedNotice(RAON);
    // DART 명시 근거는 제19회인데 KIND는 제20회다. 발행일·수량·가격은 동일하게 유지한다.
    String conflictingKind =
        readResource("/krx/notices/raon-other-round.html")
            .replace("제20회('26.03.02)", "제20회('26.02.26)");
    when(noticeClient.fetch(RAON_OTHER_ROUND.url())).thenReturn(conflictingKind);
    Ipo saved = createIpo(RAON_OTHER_ROUND);

    var preview = service.preview(saved.getId(), RAON_OTHER_ROUND.url());

    assertThat(preview.issuedSharesMatch()).isTrue();
    assertThat(preview.issuePriceMatch()).isTrue();
    assertThat(preview.datesValid()).isTrue();
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("독립적인 연결 근거가 없습니다"));
    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, RAON_OTHER_ROUND.url())))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    Ipo unchanged = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(unchanged.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(unchanged.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void conflictingRoundLabelsInsideDartDocumentCannotConfirm() throws Exception {
    stubLinkedNotice(KOIZ);
    String contradictory =
        linkedDartXml(KOIZ)
            .replace("</BODY>", syntheticRoundSection("제17회('26.01.30)") + "</BODY>");
    when(dartClient.downloadDocumentStrict(KOIZ.dartReceipt())).thenReturn(contradictory);
    Ipo saved = createIpo(KOIZ);

    var preview = service.preview(saved.getId(), KOIZ.url());

    assertThat(preview.issuedSharesMatch()).isTrue();
    assertThat(preview.issuePriceMatch()).isTrue();
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("독립적인 연결 근거가 없습니다"));
    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, KOIZ.url())))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getKrxListingNoticeUrl())
        .isNull();
  }

  @Test
  void dartDocumentFetchFailureNeverWrites() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    when(dartClient.downloadDocumentStrict(KOIZ.dartReceipt()))
        .thenThrow(
            new com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException(
                "FETCH_FAILED", "OpenDART 원문 HTTP 조회 실패"));

    assertThatThrownBy(() -> service.preview(saved.getId(), KOIZ.url()))
        .isInstanceOf(com.ipocalc.ipocatalog.service.ipo.sync.opendart.OpenDartApiException.class);
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getKrxListingNoticeUrl())
        .isNull();
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getListingDateEvidenceType())
        .isEqualTo("ESTIMATED_DART");
  }

  @Test
  void differentRoundNoticeStaysReviewOnlyAndDoesNotWrite() throws Exception {
    stubNotice(RAON_OTHER_ROUND);
    Ipo saved = createIpo(RAON_OTHER_ROUND);

    var preview = service.preview(saved.getId(), RAON_OTHER_ROUND.url());

    assertThat(preview.notice().roundLabel()).isEqualTo("제20회('26.03.02)");
    assertThat(preview.notice().listingDate()).isEqualTo(LocalDate.of(2026, 3, 24));
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reviewStatus()).isEqualTo("REVIEW_ONLY");
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("독립적인 연결 근거가 없습니다"));

    KrxListingApplyRequest otherRequest = request(preview, RAON_OTHER_ROUND.url());
    assertThatThrownBy(() -> service.apply(saved.getId(), otherRequest))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(after.getListingDate()).isEqualTo(LocalDate.of(2026, 3, 10));
    assertThat(after.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void sameListingDateButDifferentNoticeDocumentIsRejectedAndDoesNotWrite() throws Exception {
    stubLinkedNotice(KOIZ);
    Ipo saved = createIpo(KOIZ);
    var preview = service.preview(saved.getId(), KOIZ.url());
    assertThat(preview.applyAllowed()).isTrue();

    // 상장일은 같지만 내용이 바뀐 공고문은 해시 비교에서 잡힌다.
    when(noticeClient.fetch(KOIZ.url()))
        .thenReturn("<!-- reviewed-modification -->\n" + readResource("/krx/notices/koiz.html"));

    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, KOIZ.url())))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getMessage()).contains("notice hash mismatch");
            });

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(after.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void ambiguousDartListingCandidatesStayReviewOnlyAndWriteZero() throws Exception {
    String original = readResource("/krx/dart/" + KOIZ.dartReceipt() + ".xml");
    String ambiguous =
        original.replace("</DOCUMENT>", "<P>주권 상장 및 유통 예정일: 2026년 03월 11일</P></DOCUMENT>");
    stubLinkedNotice(KOIZ);
    when(dartClient.downloadDocumentStrict(KOIZ.dartReceipt())).thenReturn(ambiguous);
    Ipo saved = createIpo(KOIZ);

    var preview = service.preview(saved.getId(), KOIZ.url());
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reviewStatus()).isEqualTo("REVIEW_ONLY");
    assertThat(preview.reasons()).contains("DART 상장예정일 후보가 여러 건입니다");

    assertThatThrownBy(() -> service.apply(saved.getId(), request(preview, KOIZ.url())))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

    Ipo after = ipoRepository.findById(saved.getId()).orElseThrow();
    assertThat(after.getListingDateEvidenceType()).isEqualTo("ESTIMATED_DART");
    assertThat(after.getListingDate()).isEqualTo(KOIZ.listingDate());
    assertThat(after.getKrxListingNoticeUrl()).isNull();
  }

  @Test
  void conditionalUpdateWritesZeroWhenDateOrStockCodeChanged() {
    Ipo dateChanged = createIpo(KOIZ);
    transactionTemplate.executeWithoutResult(
        s ->
            entityManager
                .createNativeQuery("update ipos set listing_date = ?1 where id = ?2")
                .setParameter(1, java.sql.Date.valueOf(KOIZ.listingDate().plusDays(1)))
                .setParameter(2, dateChanged.getId())
                .executeUpdate());
    entityManager.clear();

    assertThat(
            ipoRepository.confirmKrxListingIfCurrent(
                dateChanged.getId(),
                KOIZ.stockCode(),
                KOIZ.dartReceipt(),
                KOIZ.listingDate(),
                KOIZ.listingDate(),
                KOIZ.url(),
                LocalDateTime.now()))
        .isZero();

    Ipo stockChanged = createIpo(AMICO);
    transactionTemplate.executeWithoutResult(
        s ->
            entityManager
                .createNativeQuery("update ipos set stock_code = ?1 where id = ?2")
                .setParameter(1, "999999")
                .setParameter(2, stockChanged.getId())
                .executeUpdate());
    entityManager.clear();

    assertThat(
            ipoRepository.confirmKrxListingIfCurrent(
                stockChanged.getId(),
                AMICO.stockCode(),
                AMICO.dartReceipt(),
                AMICO.listingDate(),
                AMICO.listingDate(),
                AMICO.url(),
                LocalDateTime.now()))
        .isZero();
  }

  // 합성 연결 근거: 실제 DART 발행실적 원문에는 KIND 발행회수와 공통인
  // '발행회수(발행일)'이 확인되지 않았다. 실제 원문 자체를 수정하지 않는다.
  private String linkedDartXml(CaseData c) {
    String dart = readResource("/krx/dart/" + c.dartReceipt() + ".xml");
    String html = readResource("/krx/notices/" + c.file());
    String round = new KrxListingNoticeParser().parse(html).roundLabel();
    assertThat(round).as("KIND fixture round").isNotBlank();
    assertThat(dart).contains("</BODY>");
    return dart.replace("</BODY>", syntheticRoundSection(round) + "</BODY>");
  }

  // 합성 유상증자 발행내역 구역: 실공시 원문에 없는 식별자를 명시적으로 삽입한다.
  private String syntheticRoundSection(String round) {
    return "<SECTION-2><TITLE>금번 유상증자 발행내역</TITLE>"
        + "<TABLE><TR><TH>발행회수(발행일)</TH><TD>"
        + round
        + "</TD></TR></TABLE></SECTION-2>";
  }

  private void stubLinkedNotice(CaseData c) {
    stubNotice(c);
    when(dartClient.downloadDocumentStrict(c.dartReceipt())).thenReturn(linkedDartXml(c));
  }

  @Test
  void quotedHistoricalRoundOutsideCurrentIssuanceSectionCannotAuthorizeWrite() {
    stubNotice(KOIZ);
    String historical =
        readResource("/krx/dart/" + KOIZ.dartReceipt() + ".xml")
            .replace(
                "</BODY>",
                "<SECTION-2><TITLE>과거 발행내역</TITLE>"
                    + "<P>발행회수(발행일): 제16회('26.01.30)</P></SECTION-2></BODY>");
    when(dartClient.downloadDocumentStrict(KOIZ.dartReceipt())).thenReturn(historical);
    Ipo saved = createIpo(KOIZ);

    var preview = service.preview(saved.getId(), KOIZ.url());

    assertThat(preview.issuedSharesMatch()).isTrue();
    assertThat(preview.issuePriceMatch()).isTrue();
    assertThat(preview.applyAllowed()).isFalse();
    assertThat(preview.reasons()).anyMatch(reason -> reason.contains("독립적인 연결 근거가 없습니다"));
    assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getKrxListingNoticeUrl())
        .isNull();
  }

  @Test
  void realDartAndKindNoticesWithoutCrossDocumentRoundEvidenceStayReviewOnly() {
    for (CaseData c : CASES) {
      stubNotice(c);
      Ipo saved = createIpo(c);
      var preview = service.preview(saved.getId(), c.url());
      assertThat(preview.applyAllowed()).isFalse();
      assertThat(preview.reviewStatus()).isEqualTo("REVIEW_ONLY");
      assertThat(preview.reasons()).anyMatch(reason -> reason.contains("독립적인 연결 근거가 없습니다"));
      assertThat(ipoRepository.findById(saved.getId()).orElseThrow().getKrxListingNoticeUrl())
          .isNull();
    }
  }

  private void stubNotice(CaseData c) {
    when(noticeClient.fetch(c.url())).thenReturn(readResource("/krx/notices/" + c.file()));
    when(dartClient.downloadDocumentStrict(c.dartReceipt()))
        .thenReturn(readResource("/krx/dart/" + c.dartReceipt() + ".xml"));
  }

  private Ipo createIpo(CaseData c) {
    Ipo ipo =
        Ipo.builder()
            .companyName("test-" + c.stockCode())
            .ipoName("test-" + c.stockCode())
            .stockCode(c.stockCode())
            .subscriptionStartDate(c.subscriptionEnd().minusDays(5))
            .subscriptionEndDate(c.subscriptionEnd())
            .listingDate(c.listingDate())
            .status(RecordStatus.ACTIVE)
            .listingDateEvidenceType("ESTIMATED_DART")
            .listingDateEvidenceReceipt(c.dartReceipt())
            .lastImportedListingDate(c.listingDate())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    Ipo saved = ipoRepository.saveAndFlush(ipo);
    createdIds.add(saved.getId());
    return saved;
  }

  private Ipo createRightsIpo(CaseData c) {
    Ipo saved =
        ipoRepository.saveAndFlush(
            Ipo.builder()
                .companyName("rights-" + c.stockCode())
                .ipoName("rights-" + c.stockCode())
                .corpCode("001019")
                .sourceReceiptNo(RIGHTS_SOURCE_RECEIPT)
                .syncSource("OPENDART")
                .stockCode(c.stockCode())
                .subscriptionStartDate(c.subscriptionEnd().minusDays(5))
                .subscriptionEndDate(c.subscriptionEnd())
                .listingDate(c.listingDate())
                .status(RecordStatus.ACTIVE)
                .listingDateEvidenceType("ESTIMATED_DART")
                .listingDateEvidenceReceipt(c.dartReceipt())
                .lastImportedListingDate(c.listingDate())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    createdIds.add(saved.getId());
    return saved;
  }

  private KrxListingApplyRequest request(KrxListingReconciliationPreview preview, String url) {
    return new KrxListingApplyRequest(
        url,
        preview.expectedDartReceiptNo(),
        preview.expectedDartListingDate(),
        preview.expectedStockCode(),
        preview.expectedIssuedShares(),
        preview.expectedIssuePrice(),
        preview.expectedKrxListingDate(),
        preview.expectedNoticeHash());
  }

  private static String readResource(String path) {
    try (InputStream in =
        KrxListingReconciliationServiceIntegrationTest.class.getResourceAsStream(path)) {
      assertThat(in).as("fixture %s", path).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private record CaseData(
      String file,
      String dartReceipt,
      String stockCode,
      LocalDate listingDate,
      LocalDate subscriptionEnd,
      String url) {}
}
