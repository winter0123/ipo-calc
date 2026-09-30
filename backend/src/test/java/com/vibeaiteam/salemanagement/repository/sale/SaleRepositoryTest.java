package com.vibeaiteam.salemanagement.repository.sale;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.accountauthentication.domain.model.account.Account;
import com.vibeaiteam.accountauthentication.repository.account.AccountRepository;
import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.allocationmanagement.repository.allocation.AllocationRepository;
import com.vibeaiteam.investormanagement.domain.model.investor.Investor;
import com.vibeaiteam.investormanagement.repository.investor.InvestorRepository;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm.SecuritiesFirmRepository;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import com.vibeaiteam.subscriptionmanagement.repository.subscription.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/spec/sale-management.md, docs/breakdown/sale-management.md - AC-001: 매도 등록 성공 — 신규
 * Sale 생성 시 status=ACTIVE, deletedAt=null - AC-006: 분할 매도 등록 성공 (여러 건 등록) — 하나의 allocationId에 여러
 * Sale 저장 가능 (D-SL-006) - AC-018: 취소(CANCELLED + deletedAt)된 기록은 목록/상세 조회에서 노출하지 않음 -
 * SaleRepositoryTest: 저장/조회, 하나의 allocationId로 여러 Sale 저장 가능 확인
 *
 * <p>sales.allocation_id는 allocations.id를 참조하는 FK(ON DELETE RESTRICT)이므로, 각 테스트는 매도를 저장하기 전에 참조할
 * Allocation(과 그 선행 Subscription)을 먼저 저장한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
class SaleRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private SaleRepository saleRepository;

  @Autowired private AllocationRepository allocationRepository;

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private InvestorRepository investorRepository;

  @Autowired private SecuritiesAccountRepository securitiesAccountRepository;

  @Autowired private SecuritiesFirmRepository securitiesFirmRepository;

  @Autowired private AccountRepository accountRepository;

  private Long createSubscription(Long ipoId, LocalDateTime createdAt) {
    Subscription subscription =
        Subscription.builder()
            .investorId(1L)
            .securitiesAccountId(1L)
            .ipoId(ipoId)
            .quantity(100L)
            .depositAmount(1000000L)
            .subscribedAt(createdAt)
            .status(RecordStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return subscriptionRepository.saveAndFlush(subscription).getId();
  }

  private Long createAllocation(
      Long subscriptionId, Long allocatedQuantity, LocalDateTime createdAt) {
    Allocation allocation =
        Allocation.builder()
            .subscriptionId(subscriptionId)
            .allocatedQuantity(allocatedQuantity)
            .allocatedAt(createdAt)
            .allocationMethod("균등배정")
            .allocationPrice(10000L)
            .status(RecordStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return allocationRepository.saveAndFlush(allocation).getId();
  }

  private Sale buildSale(
      Long allocationId, Long quantity, LocalDateTime saleDate, LocalDateTime createdAt) {
    return Sale.builder()
        .allocationId(allocationId)
        .quantity(quantity)
        .price(25000L)
        .amount(750000L)
        .saleDate(saleDate)
        .fee(1500L)
        .tax(0L)
        .otherCost(null)
        .status(RecordStatus.ACTIVE)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  private Long createAccount(LocalDateTime createdAt) {
    Account account =
        Account.builder()
            .email("test-" + UUID.randomUUID() + "@example.com")
            .passwordHash("hash")
            .name("테스트 계정")
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return accountRepository.saveAndFlush(account).getId();
  }

  private Long createSecuritiesFirm(LocalDateTime createdAt) {
    SecuritiesFirm securitiesFirm =
        SecuritiesFirm.builder()
            .name("테스트 증권사")
            .status(RecordStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return securitiesFirmRepository.saveAndFlush(securitiesFirm).getId();
  }

  private Long createInvestor(Long accountId, String displayName, LocalDateTime createdAt) {
    Investor investor =
        Investor.builder()
            .accountId(accountId)
            .displayName(displayName)
            .relationship("SELF")
            .status(RecordStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return investorRepository.saveAndFlush(investor).getId();
  }

  private Long createSecuritiesAccount(
      Long investorId, Long securitiesFirmId, LocalDateTime createdAt) {
    SecuritiesAccount securitiesAccount =
        SecuritiesAccount.builder()
            .investorId(investorId)
            .securitiesFirmId(securitiesFirmId)
            .accountNumber("12345678")
            .accountName("테스트 계좌")
            .isDefault(true)
            .status(RecordStatus.ACTIVE)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    return securitiesAccountRepository.saveAndFlush(securitiesAccount).getId();
  }

  private Subscription createSubscriptionEntity(
      Long investorId, Long securitiesAccountId, Long ipoId, LocalDateTime createdAt) {
    return Subscription.builder()
        .investorId(investorId)
        .securitiesAccountId(securitiesAccountId)
        .ipoId(ipoId)
        .quantity(100L)
        .depositAmount(5000000L)
        .subscribedAt(createdAt)
        .status(RecordStatus.ACTIVE)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  private Allocation createAllocationEntity(
      Long subscriptionId, Long allocatedQuantity, Long allocationPrice, LocalDateTime createdAt) {
    return Allocation.builder()
        .subscriptionId(subscriptionId)
        .allocatedQuantity(allocatedQuantity)
        .allocatedAt(createdAt)
        .allocationMethod("PROPORTIONAL")
        .allocationPrice(allocationPrice)
        .status(RecordStatus.ACTIVE)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }

  @Test
  void saveSaleFindByIdReturnsSaleWithActiveStatus() {
    // Given: 저장할 매도 기록이 존재함 (필수 필드 설정, status 기본값 ACTIVE)
    // - spec AC-001: 등록 시 status=ACTIVE, deletedAt=null로 생성된다

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Long allocationId = createAllocation(subscriptionId, 100L, now);
    Sale sale = buildSale(allocationId, 30L, now, now);

    // When: 저장 후 findByIdAndDeletedAtIsNull로 조회
    Sale saved = saleRepository.save(sale);
    Optional<Sale> found = saleRepository.findByIdAndDeletedAtIsNull(saved.getId());

    // Then: 저장된 매정이 id, allocationId, 매도정보로 정상 조회된다
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
    assertThat(found.get().getAllocationId()).isEqualTo(allocationId);
    assertThat(found.get().getQuantity()).isEqualTo(30L);
    assertThat(found.get().getPrice()).isEqualTo(25000L);
    assertThat(found.get().getAmount()).isEqualTo(750000L);
    assertThat(found.get().getSaleDate()).isEqualTo(now);
    assertThat(found.get().getFee()).isEqualTo(1500L);
    assertThat(found.get().getTax()).isEqualTo(0L);
    assertThat(found.get().getOtherCost()).isNull();
    assertThat(found.get().getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(found.get().getDeletedAt()).isNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  void findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDescReturnsOnlyNonDeletedSales() {
    // Given: ACTIVE 매도 2건과 취소(CANCELLED + deletedAt)된 매도 1건이 저장되어 있음
    // - spec AC-018: 취소된 매도는 목록 조회에서 제외

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Long allocationId = createAllocation(subscriptionId, 100L, now);

    saleRepository.save(buildSale(allocationId, 30L, now, now));
    saleRepository.save(buildSale(allocationId, 40L, now.plusDays(1), now.plusDays(1)));

    Sale cancelled = buildSale(allocationId, 20L, now.minusDays(1), now.minusDays(1));
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    saleRepository.saveAndFlush(cancelled);

    // When: findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc 호출
    List<Sale> result = saleRepository.findAllByDeletedAtIsNullOrderBySaleDateDescCreatedAtDesc();

    // Then: 취소된 매도가 제외되고 saleDate 내림차순, createdAt 내림차순으로 정렬된 목록이 반환된다
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getQuantity()).isEqualTo(40L); // saleDate가 더 나중
    assertThat(result.get(1).getQuantity()).isEqualTo(30L);
  }

  @Test
  void findByIdAndDeletedAtIsNullExcludesCancelledSale() {
    // Given: ACTIVE 매도와 취소된 매도가 저장되어 있음
    // - spec AC-011: 취소(CANCELLED + deletedAt)된 기록은 상세 조회에서 노출하지 않음

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Long allocationId = createAllocation(subscriptionId, 100L, now);

    Sale active = saleRepository.save(buildSale(allocationId, 30L, now, now));

    Sale cancelled = buildSale(allocationId, 20L, now, now);
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    Sale savedCancelled = saleRepository.saveAndFlush(cancelled);

    // When: 각각 findByIdAndDeletedAtIsNull 호출
    Optional<Sale> activeResult = saleRepository.findByIdAndDeletedAtIsNull(active.getId());
    Optional<Sale> cancelledResult =
        saleRepository.findByIdAndDeletedAtIsNull(savedCancelled.getId());

    // Then: ACTIVE 매도만 반환되고 취소된 매도는 반환되지 않는다
    assertThat(activeResult).isPresent();
    assertThat(activeResult.get().getQuantity()).isEqualTo(30L);
    assertThat(cancelledResult).isEmpty();
  }

  @Test
  void saveMultipleSalesWithSameAllocationIdAllowed() {
    // Given: 하나의 Allocation에 여러 건의 매도가 저장되어도 유니크 제약이 없음 (D-SL-006)
    // - spec AC-006: 분할 매도 등록 성공 (여러 건 등록)

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Long allocationId = createAllocation(subscriptionId, 100L, now);

    // When: 동일 allocationId로 여러 건의 Sale 저장
    Sale sale1 = saleRepository.saveAndFlush(buildSale(allocationId, 30L, now, now));
    Sale sale2 =
        saleRepository.saveAndFlush(
            buildSale(allocationId, 40L, now.plusHours(1), now.plusHours(1)));
    Sale sale3 =
        saleRepository.saveAndFlush(
            buildSale(allocationId, 20L, now.plusHours(2), now.plusHours(2)));

    // Then: 유니크 위반 없이 모두 저장된다 (allocation_id에 유니크 제약이 없음)
    assertThat(sale1.getId()).isNotNull();
    assertThat(sale2.getId()).isNotNull();
    assertThat(sale3.getId()).isNotNull();
    assertThat(sale1.getAllocationId()).isEqualTo(allocationId);
    assertThat(sale2.getAllocationId()).isEqualTo(allocationId);
    assertThat(sale3.getAllocationId()).isEqualTo(allocationId);

    // 그리고 누적 매도수량 합계 조회가 정상 동작함
    Long totalQuantity = saleRepository.sumActiveQuantityByAllocationId(allocationId);
    assertThat(totalQuantity).isEqualTo(90L); // 30 + 40 + 20
  }

  @Test
  void findByAllocationIdAndDeletedAtIsNullAndStatusReturnsActiveSalesOnly() {
    // Given: 동일 allocationId에 ACTIVE 2건, CANCELLED 1건이 저장되어 있음

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Long allocationId = createAllocation(subscriptionId, 100L, now);

    saleRepository.save(buildSale(allocationId, 30L, now, now));
    saleRepository.save(buildSale(allocationId, 40L, now.plusHours(1), now.plusHours(1)));

    Sale cancelled = buildSale(allocationId, 20L, now.minusHours(1), now.minusHours(1));
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    saleRepository.saveAndFlush(cancelled);

    // When: findByAllocationIdAndDeletedAtIsNullAndStatus로 ACTIVE만 조회
    List<Sale> activeSales =
        saleRepository
            .findByAllocationIdAndDeletedAtIsNullAndStatusOrderBySaleDateDescCreatedAtDesc(
                allocationId, RecordStatus.ACTIVE);

    // Then: ACTIVE 상태인 2건만 반환됨
    assertThat(activeSales).hasSize(2);
    assertThat(activeSales).allMatch(s -> s.getStatus() == RecordStatus.ACTIVE);
  }

  @Test
  void sumActiveQuantityByAllocationIdReturnsCorrectSum() {
    // Given: 동일 allocationId에 ACTIVE 3건, CANCELLED 1건이 저장되어 있음

    LocalDateTime now = LocalDateTime.now();
    Long subscriptionId = createSubscription(1L, now);
    Long allocationId = createAllocation(subscriptionId, 100L, now);

    saleRepository.save(buildSale(allocationId, 30L, now, now));
    saleRepository.save(buildSale(allocationId, 40L, now.plusHours(1), now.plusHours(1)));
    saleRepository.save(buildSale(allocationId, 20L, now.plusHours(2), now.plusHours(2)));

    Sale cancelled = buildSale(allocationId, 50L, now.minusHours(1), now.minusHours(1));
    cancelled.setStatus(RecordStatus.CANCELLED);
    cancelled.setDeletedAt(now);
    saleRepository.saveAndFlush(cancelled);

    // When: sumActiveQuantityByAllocationId 호출
    Long sum = saleRepository.sumActiveQuantityByAllocationId(allocationId);

    // Then: ACTIVE 상태인 3건의 수량 합계(90)만 반환됨 (CANCELLED 제외)
    assertThat(sum).isEqualTo(90L);
  }

  // ===========================================
  // 신규 메서드: findAllByStatusAndInvestorAccountId 테스트 (Epic 12 SF-1 추가)
  // ===========================================

  @Test
  void findAllByStatusAndInvestorAccountIdFiltersByAccount() {
    // Given: 계정 1의 투자자, 계정 2의 투자자 각각 생성
    LocalDateTime now = LocalDateTime.now();

    Long account1Id = createAccount(now);
    Long account2Id = createAccount(now);

    Long securitiesFirmId = createSecuritiesFirm(now);

    Long investor1Id = createInvestor(account1Id, "투자자1", now);
    Long investor2Id = createInvestor(account2Id, "투자자2", now);

    Long securitiesAccount1Id = createSecuritiesAccount(investor1Id, securitiesFirmId, now);
    Long securitiesAccount2Id = createSecuritiesAccount(investor2Id, securitiesFirmId, now);

    Subscription subscription1 =
        createSubscriptionEntity(investor1Id, securitiesAccount1Id, 1L, now);
    Long subscription1Id = subscriptionRepository.saveAndFlush(subscription1).getId();

    Subscription subscription2 =
        createSubscriptionEntity(investor2Id, securitiesAccount2Id, 1L, now);
    Long subscription2Id = subscriptionRepository.saveAndFlush(subscription2).getId();

    Allocation allocation1 = createAllocationEntity(subscription1Id, 50L, 10000L, now);
    Long allocation1Id = allocationRepository.saveAndFlush(allocation1).getId();

    Allocation allocation2 = createAllocationEntity(subscription2Id, 50L, 10000L, now);
    Long allocation2Id = allocationRepository.saveAndFlush(allocation2).getId();

    // Sale 테이블에 데이터 삽입 (계정 1: ACTIVE 2건, CANCELLED 1건 / 계정 2: ACTIVE 1건)
    Sale sale1 =
        Sale.builder()
            .allocationId(allocation1Id)
            .quantity(10L)
            .amount(100000L)
            .saleDate(now)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Sale sale2 =
        Sale.builder()
            .allocationId(allocation1Id)
            .quantity(10L)
            .amount(100000L)
            .saleDate(now)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Sale sale3 =
        Sale.builder()
            .allocationId(allocation1Id)
            .quantity(10L)
            .amount(100000L)
            .saleDate(now)
            .status(RecordStatus.CANCELLED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Sale sale4 =
        Sale.builder()
            .allocationId(allocation2Id)
            .quantity(20L)
            .amount(200000L)
            .saleDate(now)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    entityManager.persistAndFlush(sale1);
    entityManager.persistAndFlush(sale2);
    entityManager.persistAndFlush(sale3);
    entityManager.persistAndFlush(sale4);

    // When: 계정 1의 ACTIVE Sale만 조회
    List<Sale> account1ActiveSales =
        saleRepository.findAllByStatusAndInvestorAccountId(RecordStatus.ACTIVE, account1Id);

    // Then: 계정 1의 ACTIVE Sale 2건만 조회됨 (CANCELLED 제외, 계정 2 데이터 제외)
    assertThat(account1ActiveSales).hasSize(2);
    assertThat(account1ActiveSales)
        .extracting(Sale::getId)
        .containsExactlyInAnyOrder(sale1.getId(), sale2.getId());
    assertThat(account1ActiveSales).allMatch(s -> s.getStatus() == RecordStatus.ACTIVE);
  }

  @Test
  void findAllByStatusAndInvestorAccountIdReturnsEmptyWhenNoActiveSales() {
    // Given: 데이터 없음

    // When
    List<Sale> result =
        saleRepository.findAllByStatusAndInvestorAccountId(RecordStatus.ACTIVE, 999L);

    // Then: 빈 리스트 반환
    assertThat(result).isEmpty();
  }

  @Test
  void findAllByStatusAndInvestorAccountIdExcludesCancelledSales() {
    // Given: 계정 1의 투자자/구독/배정 생성
    LocalDateTime now = LocalDateTime.now();
    Long accountId = createAccount(now);
    Long securitiesFirmId = createSecuritiesFirm(now);

    Long investorId = createInvestor(accountId, "투자자1", now);
    Long securitiesAccountId = createSecuritiesAccount(investorId, securitiesFirmId, now);

    Subscription subscription = createSubscriptionEntity(investorId, securitiesAccountId, 1L, now);
    Long subscriptionId = subscriptionRepository.saveAndFlush(subscription).getId();

    Allocation allocation = createAllocationEntity(subscriptionId, 50L, 10000L, now);
    Long allocationId = allocationRepository.saveAndFlush(allocation).getId();

    // ACTIVE 1건, CANCELLED 1건 생성
    Sale activeSale =
        Sale.builder()
            .allocationId(allocationId)
            .quantity(10L)
            .amount(100000L)
            .saleDate(now)
            .status(RecordStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

    Sale cancelledSale =
        Sale.builder()
            .allocationId(allocationId)
            .quantity(10L)
            .amount(100000L)
            .saleDate(now)
            .status(RecordStatus.CANCELLED)
            .createdAt(now)
            .updatedAt(now)
            .build();

    entityManager.persistAndFlush(activeSale);
    entityManager.persistAndFlush(cancelledSale);

    // When
    List<Sale> result =
        saleRepository.findAllByStatusAndInvestorAccountId(RecordStatus.ACTIVE, accountId);

    // Then: ACTIVE만 조회됨
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(RecordStatus.ACTIVE);
    assertThat(result.get(0).getId()).isEqualTo(activeSale.getId());
  }
}
