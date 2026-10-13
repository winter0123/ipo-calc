package com.vibeaiteam.ipocatalog.service.ipo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoInsightResponse;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoNotFoundException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.OpenDartCorpDisclosureClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.opendart.dto.OpenDartCompanyOverview;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.UstockPlusCalendarClient;
import com.vibeaiteam.ipocatalog.service.ipo.sync.ustockplus.dto.UstockPlusIpoDetail;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link IpoInsightService} 단위 테스트. 외부 호출은 목으로 대신한다. */
class IpoInsightServiceTest {

  private final IpoRepository ipoRepository = mock(IpoRepository.class);
  private final UstockPlusCalendarClient ustock = mock(UstockPlusCalendarClient.class);
  private final OpenDartCorpDisclosureClient dart = mock(OpenDartCorpDisclosureClient.class);
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-13T00:00:00Z"));
  private IpoInsightService service;

  @BeforeEach
  void setUp() {
    service = new IpoInsightService(ipoRepository, ustock, dart, clock);
    Ipo ipo = Ipo.builder().id(1L).stockCode("0013Y0").corpCode("01234567").build();
    when(ipoRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ipo));
  }

  private static UstockPlusIpoDetail detail(String statementUrl) {
    return new UstockPlusIpoDetail(
        "생체 소재 의료기기",
        List.of("의료기기"),
        new UstockPlusIpoDetail.SubscriptionInfo(
            List.of(
                new UstockPlusIpoDetail.FirmRate(
                    "하나증권", 213000L, new BigDecimal("0.72"), new BigDecimal("1370.7")))),
        new UstockPlusIpoDetail.DemandForecast(
            LocalDate.of(2026, 9, 16),
            LocalDate.of(2026, 9, 22),
            new BigDecimal("1096.56"),
            new BigDecimal("30.62"),
            88070550500L),
        new UstockPlusIpoDetail.IpoInformation(
            23786847000L,
            7287888000L,
            815000L,
            3747683L,
            new BigDecimal("76.67"),
            new BigDecimal("23.33"),
            statementUrl));
  }

  @Test
  void mergesUstockPlusAndDart() {
    when(ustock.fetchIpoDetail("0013Y0"))
        .thenReturn(Optional.of(detail("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=1")));
    when(dart.getCompanyOverview("01234567"))
        .thenReturn(Optional.of(new OpenDartCompanyOverview("000", "김성포", "20151027", "경기도 성남시")));

    IpoInsightResponse r = service.getInsight(1L);

    assertThat(r.ceoName()).isEqualTo("김성포");
    assertThat(r.establishedOn()).isEqualTo(LocalDate.of(2015, 10, 27));
    assertThat(r.institutionalCompetitionRate()).isEqualByComparingTo("1096.56");
    assertThat(r.lockUpCommitmentRatio()).isEqualByComparingTo("30.62");
    assertThat(r.recentSales()).isEqualTo(23786847000L);
    assertThat(r.brokers())
        .extracting(IpoInsightResponse.BrokerAllocation::name)
        .containsExactly("하나증권");
    assertThat(r.registrationStatementUrl()).startsWith("https://dart.fss.or.kr/");
  }

  @Test
  void returnsEmptyFieldsWhenSourcesFailAndDropsNonDartLinks() {
    when(ustock.fetchIpoDetail("0013Y0")).thenReturn(Optional.of(detail("javascript:alert(1)")));
    when(dart.getCompanyOverview("01234567")).thenReturn(Optional.empty());

    IpoInsightResponse r = service.getInsight(1L);

    assertThat(r.ceoName()).isNull();
    assertThat(r.registrationStatementUrl()).isNull();
    assertThat(r.recentSales()).isEqualTo(23786847000L);
  }

  @Test
  void cachesForOneHour() {
    when(ustock.fetchIpoDetail("0013Y0")).thenReturn(Optional.empty());
    when(dart.getCompanyOverview("01234567")).thenReturn(Optional.empty());

    service.getInsight(1L);
    clock.advanceMinutes(59);
    service.getInsight(1L);
    verify(ustock, times(1)).fetchIpoDetail("0013Y0");

    clock.advanceMinutes(2);
    service.getInsight(1L);
    verify(ustock, times(2)).fetchIpoDetail("0013Y0");
  }

  @Test
  void unknownIpoThrowsNotFound() {
    when(ipoRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getInsight(9L)).isInstanceOf(IpoNotFoundException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advanceMinutes(long minutes) {
      now = now.plusSeconds(minutes * 60);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Asia/Seoul");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
