package com.vibeaiteam.ipocatalog.service.ipo.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.ipocatalog.controller.ipo.dto.IpoInsightResponse;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.ipocatalog.exception.IpoNotFoundException;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoDetailRepository;
import com.vibeaiteam.ipocatalog.repository.ipo.IpoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** IpoDetailService: 상세 저장(upsert)과 조회. 실제 DB를 쓴다. */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class IpoDetailServiceTest {

  @Autowired private IpoDetailService service;
  @Autowired private IpoRepository ipoRepository;
  @Autowired private IpoDetailRepository detailRepository;

  private Long ipoId;

  @BeforeEach
  void setUp() {
    LocalDateTime now = LocalDateTime.now();
    Ipo ipo =
        ipoRepository.save(
            Ipo.builder()
                .companyName("상세테스트")
                .ipoName("상세테스트-" + System.nanoTime())
                .subscriptionStartDate(LocalDate.of(2026, 10, 1))
                .subscriptionEndDate(LocalDate.of(2026, 10, 2))
                .createdAt(now)
                .updatedAt(now)
                .build());
    ipoId = ipo.getId();
  }

  @AfterEach
  void tearDown() {
    detailRepository.deleteAll();
    ipoRepository.deleteAll();
  }

  @Test
  void saveTwiceKeepsOneRowAndOverwritesValues() {
    service.save(ipoId, snapshot("김성포", "20261007000328"));
    service.save(ipoId, snapshot("박대표", "20261008000111"));

    assertThat(detailRepository.count()).isEqualTo(1);
    IpoInsightResponse r = service.getInsight(ipoId);
    assertThat(r.ceoName()).isEqualTo("박대표");
    assertThat(r.sourceUrl()).endsWith("rcpNo=20261008000111");
    assertThat(r.syncedAt()).isNotNull();
    assertThat(r.financials()).hasSize(1);
    assertThat(r.underwriters().get(0).name()).isEqualTo("KB증권");
  }

  @Test
  void getInsightWithoutDetailReturnsEmptyValues() {
    IpoInsightResponse r = service.getInsight(ipoId);

    assertThat(r.syncedAt()).isNull();
    assertThat(r.sourceUrl()).isNull();
    assertThat(r.financials()).isEmpty();
    assertThat(r.floatSchedule()).isEmpty();
    assertThat(r.underwriters()).isEmpty();
  }

  @Test
  void nonNumericReceiptNumberYieldsNoSourceUrl() {
    service.save(ipoId, snapshot("김성포", "../evil"));

    assertThat(service.getInsight(ipoId).sourceUrl()).isNull();
  }

  @Test
  void unknownIpoThrowsNotFound() {
    assertThatThrownBy(() -> service.getInsight(-1L)).isInstanceOf(IpoNotFoundException.class);
    assertThatThrownBy(() -> service.save(-1L, snapshot("a", "20261007000328")))
        .isInstanceOf(IpoNotFoundException.class);
  }

  private static IpoDetailSnapshot snapshot(String ceo, String rceptNo) {
    return new IpoDetailSnapshot(
        rceptNo,
        "[기재정정]투자설명서",
        ceo,
        LocalDate.of(2015, 10, 27),
        "경기도",
        100L,
        2308265000L,
        "김성포",
        new BigDecimal("30.10"),
        new BigDecimal("1096.56"),
        new BigDecimal("30.62"),
        List.of(new IpoDetailSnapshot.Financial("제11기", 23786846687L, null, 7287887715L)),
        List.of(new IpoDetailSnapshot.FloatStep("상장일 유통가능", 6347310L, new BigDecimal("23.33"))),
        List.of(new IpoDetailSnapshot.Underwriter("KB증권", "대표", 4000000L)));
  }
}
