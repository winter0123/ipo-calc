package com.vibeaiteam.ipocatalog.service.ipo;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoInsightResponse;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoInsightResponse.BrokerAllocation;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoNotFoundException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartCompanyOverview;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.dto.UstockPlusIpoDetail;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 공모주 상세 화면의 부가 정보를 ustockplus(수요예측·증권사별 청약·실적)와 OpenDART(기업개황)에서 모은다.
 *
 * <p>DB에 저장하지 않고 화면을 열 때 조회한다. 외부 호출을 줄이려고 공모주별로 1시간 메모리에 담아 둔다. 어느 한쪽이 실패해도 나머지 값으로 응답한다.
 *
 * <p>ponytail: 메모리 캐시라 서버 한 대 기준이고 항목을 지우지 않는다(공모주 수만큼만 쌓임). 여러 대로 늘리면 공용 캐시로 바꾼다.
 */
@Service
public class IpoInsightService {

  private static final Duration TTL = Duration.ofHours(1);
  private static final DateTimeFormatter DART_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String DART_URL_PREFIX = "https://dart.fss.or.kr/";

  private final IpoRepository ipoRepository;
  private final UstockPlusCalendarClient ustockPlusClient;
  private final OpenDartCorpDisclosureClient openDartClient;
  private final Clock clock;
  private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

  /**
   * 생성자.
   *
   * @param ipoRepository 공모주 저장소
   * @param ustockPlusClient ustockplus 클라이언트
   * @param openDartClient OpenDART 클라이언트
   * @param clock 캐시 만료 판단용 시계
   */
  public IpoInsightService(
      IpoRepository ipoRepository,
      UstockPlusCalendarClient ustockPlusClient,
      OpenDartCorpDisclosureClient openDartClient,
      Clock clock) {
    this.ipoRepository = ipoRepository;
    this.ustockPlusClient = ustockPlusClient;
    this.openDartClient = openDartClient;
    this.clock = clock;
  }

  /**
   * 공모주 부가 정보를 조회한다.
   *
   * @param ipoId 공모주 ID
   * @return 부가 정보(모르는 값은 null)
   * @throws IpoNotFoundException 공모주가 없거나 삭제된 경우
   */
  public IpoInsightResponse getInsight(Long ipoId) {
    Ipo ipo =
        ipoRepository
            .findByIdAndDeletedAtIsNull(ipoId)
            .orElseThrow(() -> new IpoNotFoundException("IPO를 찾을 수 없습니다."));
    Instant now = clock.instant();
    Cached hit = cache.get(ipoId);
    if (hit != null && hit.at().plus(TTL).isAfter(now)) {
      return hit.value();
    }
    IpoInsightResponse value =
        build(
            ustockPlusClient.fetchIpoDetail(ipo.getStockCode()).orElse(null),
            openDartClient.getCompanyOverview(ipo.getCorpCode()).orElse(null));
    cache.put(ipoId, new Cached(now, value));
    return value;
  }

  private static IpoInsightResponse build(UstockPlusIpoDetail us, OpenDartCompanyOverview dart) {
    UstockPlusIpoDetail.DemandForecast df = us == null ? null : us.demandForecastResponse();
    UstockPlusIpoDetail.IpoInformation info = us == null ? null : us.ipoInformation();
    UstockPlusIpoDetail.SubscriptionInfo sub =
        us == null ? null : us.subscriptionAndListedInformation();
    List<BrokerAllocation> brokers =
        sub == null || sub.securitiesFirmCompetitionRates() == null
            ? List.of()
            : sub.securitiesFirmCompetitionRates().stream()
                .filter(Objects::nonNull)
                .map(
                    r ->
                        new BrokerAllocation(
                            r.securitiesFirmName(),
                            r.numberOfDistributedShares(),
                            r.equalityDistributionNumberPerPerson(),
                            r.proportionalDistributionRatio()))
                .toList();
    return new IpoInsightResponse(
        us == null ? null : blankToNull(us.description()),
        us == null || us.keywords() == null ? List.of() : us.keywords(),
        dart == null ? null : blankToNull(dart.ceoName()),
        dart == null ? null : parseDartDate(dart.establishedOn()),
        dart == null ? null : blankToNull(dart.address()),
        df == null ? null : df.demandForecastStartDate(),
        df == null ? null : df.demandForecastEndDate(),
        df == null ? null : df.instCompetitiveness(),
        df == null ? null : df.lockUpPromiseRatio(),
        info == null ? null : info.lockupRatio(),
        info == null ? null : info.floatShareRatio(),
        info == null ? null : info.numberOfListedStockShare(),
        info == null ? null : info.numberOfIpoShares(),
        df == null ? null : df.offerPriceBasedMarketCap(),
        info == null ? null : info.totalSales(),
        info == null ? null : info.netProfit(),
        info == null ? null : dartUrlOrNull(info.registrationStatementUrl()),
        brokers);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static LocalDate parseDartDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim(), DART_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  // 외부 응답의 링크를 그대로 화면에 걸지 않도록 DART 주소만 통과시킨다.
  private static String dartUrlOrNull(String url) {
    return url != null && url.startsWith(DART_URL_PREFIX) ? url : null;
  }

  private record Cached(Instant at, IpoInsightResponse value) {}
}
