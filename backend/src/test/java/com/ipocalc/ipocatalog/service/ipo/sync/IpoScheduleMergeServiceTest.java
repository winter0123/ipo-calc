package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.IpoCalcApplication;
import com.ipocalc.accountauthentication.domain.model.RecordStatus;
import com.ipocalc.ipocatalog.domain.model.ipo.Ipo;
import com.ipocalc.ipocatalog.repository.ipo.IpoRepository;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.ipocalc.ipocatalog.service.ipo.sync.ustockplus.UstockPlusScheduleDates;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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

/** {@link IpoScheduleMergeService} 단위 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class IpoScheduleMergeServiceTest {

  @MockitoBean private UstockPlusCalendarClient ustockPlusCalendarClient;

  @MockitoBean private IpoRepository ipoRepository;

  @Autowired private TransactionTemplate transactionTemplate;

  private IpoScheduleMergeService mergeService;

  private final ArgumentCaptor<Ipo> ipoCaptor = ArgumentCaptor.forClass(Ipo.class);

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
  private static final RecordStatus STATUS_ACTIVE = RecordStatus.ACTIVE;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @BeforeEach
  void setUp() {
    mergeService =
        new IpoScheduleMergeService(
            ustockPlusCalendarClient,
            ipoRepository,
            transactionTemplate,
            Clock.fixed(TODAY.atStartOfDay(SEOUL).toInstant(), SEOUL));
  }

  private UstockPlusScheduleDates createSchedule(
      String stockCode,
      String stockName,
      LocalDate listingDate,
      LocalDate allotmentDate,
      LocalDate refundDate) {
    return new UstockPlusScheduleDates(
        stockCode, stockName, listingDate, allotmentDate, refundDate);
  }

  private Ipo createAutoSyncIpo(
      Long id,
      String corpCode,
      String ipoName,
      String stockCode,
      LocalDate listingDate,
      LocalDate allotmentDate,
      LocalDate refundDate) {
    return Ipo.builder()
        .id(id)
        .companyName(ipoName)
        .ipoName(ipoName)
        .corpCode(corpCode)
        .stockCode(stockCode)
        .subscriptionStartDate(LocalDate.of(2026, 11, 1))
        .subscriptionEndDate(LocalDate.of(2026, 11, 5))
        .listingDate(listingDate)
        .allotmentDate(allotmentDate)
        .refundDate(refundDate)
        .offeringShareCount(1_000_000L)
        .status(STATUS_ACTIVE)
        .syncSource("OPENDART")
        .lastSyncedAt(LocalDateTime.now().minusDays(1))
        .createdAt(LocalDateTime.now().minusDays(10))
        .updatedAt(LocalDateTime.now().minusDays(1))
        .build();
  }

  private Ipo createManualIpo(Long id, String corpCode, String ipoName, String stockCode) {
    return Ipo.builder()
        .id(id)
        .companyName(ipoName)
        .ipoName(ipoName)
        .corpCode(corpCode)
        .stockCode(stockCode)
        .subscriptionStartDate(LocalDate.of(2026, 11, 1))
        .subscriptionEndDate(LocalDate.of(2026, 11, 5))
        .offeringShareCount(1_000_000L)
        .status(STATUS_ACTIVE)
        .syncSource(null) // 수동 등록
        .createdAt(LocalDateTime.now().minusDays(10))
        .updatedAt(LocalDateTime.now().minusDays(10))
        .build();
  }

  @Test
  void merge_matchesByStockCode_andUpdatesAllThreeDates() {
    // given: 종목코드로 매칭되는 자동 동기화 레코드 존재
    Ipo existing = createAutoSyncIpo(1L, "01658035", "진코스텍", "250030", null, null, null);
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getStockCode()).isEqualTo("250030");
    assertThat(saved.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(saved.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(saved.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(saved.getSyncSource()).isEqualTo("OPENDART+USTOCKPLUS");
  }

  @Test
  void merge_matchesByStockCode_preservesExistingListingDate_updatesAllotmentAndRefund() {
    // given: 종목코드 매칭, listingDate 이미 있음 (수동 입력 보존)
    Ipo existing =
        createAutoSyncIpo(1L, "01658035", "진코스텍", "250030", LocalDate.of(2026, 11, 25), null, null);
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    // listingDate 보존됨
    assertThat(saved.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 25));
    // allotmentDate, refundDate 갱신됨
    assertThat(saved.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(saved.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 12));
  }

  @Test
  void merge_skipsManualRecord_whenMatchedByStockCode() {
    // given: 종목코드 매칭되나 수동 등록 레코드 (syncSource == null)
    Ipo manual = createManualIpo(1L, "01658035", "진코스텍", "250030");
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(manual));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
    verify(ipoRepository, never()).saveAndFlush(any(Ipo.class));
  }

  @Test
  void merge_matchesByNormalizedName_whenStockCodeNull_andExactlyOneMatch() {
    // given: 종목코드 매칭 실패, stockCode=null인 자동 동기화 레코드 중 정규화 이름 매칭 1건
    Ipo candidate = createAutoSyncIpo(1L, "01658035", "(주)진코스텍", null, null, null, null);
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of(candidate));

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "주식회사 진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    // stockCode가 채워짐
    assertThat(saved.getStockCode()).isEqualTo("250030");
    assertThat(saved.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(saved.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(saved.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 12));
  }

  @Test
  void merge_skipsWhenNameMatchZeroCandidates() {
    // given: 종목코드 매칭 실패, 이름 매칭 후보 0건
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "존재않는회사",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
    verify(ipoRepository, never()).saveAndFlush(any(Ipo.class));
  }

  @Test
  void merge_skipsWhenNameMatchMultipleCandidates() {
    // given: 종목코드 매칭 실패, 이름 매칭 후보 2건 (모호함 → 건너뜀)
    Ipo candidate1 = createAutoSyncIpo(1L, "01658035", "진코스텍", null, null, null, null);
    Ipo candidate2 = createAutoSyncIpo(2L, "01658036", "진코스텍", null, null, null, null);
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of(candidate1, candidate2));

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
    verify(ipoRepository, never()).saveAndFlush(any(Ipo.class));
  }

  @Test
  void merge_skipsWhenStockNameBlank() {
    // given: stockName이 없으면 회사명 매칭 불가
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
  }

  @Test
  void merge_doesNotSaveWhenNoFieldChanges() {
    // given: 이미 모든 날짜가 같은 값으로 채워져 있음
    Ipo existing =
        createAutoSyncIpo(
            1L,
            "01658035",
            "진코스텍",
            "250030",
            LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 12));
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
    verify(ipoRepository, never()).saveAndFlush(any(Ipo.class));
  }

  @Test
  void merge_onlyUpdatesAllotmentAndRefund_whenListingDateNullInUstockplus() {
    // given: ustockplus에 listingDate가 null, allotmentDate/refundDate만 있음
    Ipo existing = createAutoSyncIpo(1L, "01658035", "진코스텍", "250030", null, null, null);
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030", "진코스텍", null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getListingDate()).isNull(); // ustockplus가 null이면 건드리지 않음
    assertThat(saved.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(saved.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 12));
  }

  @Test
  void merge_doesNotOverwriteDartAllotmentAndRefund_butFillsListingDate() {
    // given: DART 값이 이미 있고 ustockplus는 다른 값을 줌
    Ipo existing =
        createAutoSyncIpo(
            1L,
            "01658035",
            "진코스텍",
            "250030",
            null,
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 10));
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(
            List.of(
                createSchedule(
                    "250030",
                    "진코스텍",
                    LocalDate.of(2026, 11, 20),
                    LocalDate.of(2026, 11, 9),
                    LocalDate.of(2026, 11, 12))));

    int merged = mergeService.merge(TODAY);

    assertThat(merged).isEqualTo(1);
    verify(ipoRepository).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getValue();
    assertThat(saved.getListingDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(saved.getAllotmentDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    assertThat(saved.getRefundDate()).isEqualTo(LocalDate.of(2026, 11, 10));
  }

  @Test
  void merge_continuesOnSingleItemException() {
    // given: 첫 번째 항목에서 예외 발생, 두 번째 정상
    Ipo existing1 = createAutoSyncIpo(1L, "01658035", "진코스텍", "250030", null, null, null);
    Ipo existing2 = createAutoSyncIpo(2L, "01028933", "멜콘", "170190", null, null, null);

    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing1));
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("170190", STATUS_ACTIVE))
        .thenReturn(Optional.of(existing2));
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of());

    // 첫 번째 저장 시에만 예외 발생 (두 번째는 정상 저장, 반환형 메서드라 doAnswer 사용)
    doThrow(new DataIntegrityViolationException("중복 키"))
        .doAnswer(invocation -> invocation.getArgument(0))
        .when(ipoRepository)
        .saveAndFlush(any(Ipo.class));

    List<UstockPlusScheduleDates> schedules =
        List.of(
            createSchedule(
                "250030",
                "진코스텍",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12)),
            createSchedule(
                "170190",
                "멜콘",
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 17)));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(schedules);

    // when
    int merged = mergeService.merge(TODAY);

    // then: 두 번째만 성공
    assertThat(merged).isEqualTo(1);
    verify(ipoRepository, times(2)).saveAndFlush(ipoCaptor.capture());
    Ipo saved = ipoCaptor.getAllValues().get(1);
    assertThat(saved.getStockCode()).isEqualTo("170190");
  }

  @Test
  void merge_returnsZeroWhenClientReturnsEmpty() {
    // given: ustockplus 빈 리스트 반환
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(List.of());

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
  }

  @Test
  void merge_returnsZeroWhenClientThrowsException() {
    // given: ustockplus 호출 시 예외
    when(ustockPlusCalendarClient.fetchScheduleDates(any(), any()))
        .thenThrow(new RuntimeException("네트워크 오류"));

    // when
    int merged = mergeService.merge(TODAY);

    // then
    assertThat(merged).isEqualTo(0);
  }

  @Test
  void normalizeName_removesCorpPrefixAndWhitespaceAndLowercases() {
    // given/when/then
    assertThat(IpoScheduleMergeService.normalizeName("(주)진코스텍")).isEqualTo("진코스텍");
    assertThat(IpoScheduleMergeService.normalizeName("㈜진코스텍")).isEqualTo("진코스텍");
    assertThat(IpoScheduleMergeService.normalizeName("주식회사 진코스텍")).isEqualTo("진코스텍");
    assertThat(IpoScheduleMergeService.normalizeName("진코스텍")).isEqualTo("진코스텍");
    assertThat(IpoScheduleMergeService.normalizeName(" (주) 진코스텍 ")).isEqualTo("진코스텍");
    assertThat(IpoScheduleMergeService.normalizeName(null)).isEqualTo("");
  }

  @Test
  void merge_nameMatch_savesWhenOnlyStockCodeWasFilled() {
    // 날짜가 이미 모두 채워져 있어도 종목코드가 새로 채워지면 저장되어야 한다
    Ipo existing =
        createAutoSyncIpo(
            1L,
            "01658035",
            "진코스텍",
            null,
            LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 12));
    when(ipoRepository.findByStockCodeAndStatusAndDeletedAtIsNull("250030", STATUS_ACTIVE))
        .thenReturn(Optional.empty());
    when(ipoRepository.findByStockCodeIsNullAndSyncSourceIsNotNullAndStatusAndDeletedAtIsNull(
            STATUS_ACTIVE))
        .thenReturn(List.of(existing));
    when(ustockPlusCalendarClient.fetchScheduleDates(TODAY.minusDays(30), TODAY.plusDays(59)))
        .thenReturn(
            List.of(
                createSchedule(
                    "250030",
                    "진코스텍",
                    LocalDate.of(2026, 11, 20),
                    LocalDate.of(2026, 11, 10),
                    LocalDate.of(2026, 11, 12))));

    assertThat(mergeService.merge(TODAY)).isEqualTo(1);
    assertThat(existing.getStockCode()).isEqualTo("250030");
    org.mockito.Mockito.verify(ipoRepository).saveAndFlush(existing);
  }
}
