package com.vibeaiteam.investmentqueryapi.service.investmenthistory;

import com.vibeaiteam.allocationmanagement.domain.model.allocation.Allocation;
import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.AllocationSummaryDto;
import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.InvestmentHistoryDto;
import com.vibeaiteam.investmentqueryapi.dto.investmenthistory.SaleSummaryDto;
import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import com.vibeaiteam.salemanagement.domain.model.sale.Sale;
import com.vibeaiteam.subscriptionmanagement.domain.model.subscription.Subscription;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 청약·배정·매도 데이터를 투자 이력 DTO로 조립한다. */
@Component
public class InvestmentHistoryAssembler {

  /** 청약 1건과 관련 배정·매도를 투자 이력 DTO로 변환한다. */
  public InvestmentHistoryDto toDto(
      Subscription subscription,
      Allocation allocation,
      List<Sale> activeSales,
      Map<Long, Ipo> ipoMap,
      String investorName,
      String securitiesFirmName) {
    InvestmentHistoryDto dto = new InvestmentHistoryDto();

    dto.setId(subscription.getId());
    dto.setSubscribedAt(subscription.getSubscribedAt().toString());
    dto.setIpoId(subscription.getIpoId());
    dto.setIpoName(
        subscription.getIpoId() != null && ipoMap != null
            ? ipoMap.get(subscription.getIpoId()).getIpoName()
            : null);
    dto.setIpoFixedPrice(
        subscription.getIpoId() != null && ipoMap != null
            ? ipoMap.get(subscription.getIpoId()).getFixedPrice()
            : null);
    dto.setSubscriptionQuantity(subscription.getQuantity());
    dto.setSubscriptionStatus(subscription.getStatus().name());
    dto.setInvestorName(investorName);
    dto.setSecuritiesFirmName(securitiesFirmName);

    if (allocation != null) {
      AllocationSummaryDto allocationDto = new AllocationSummaryDto();
      allocationDto.setId(allocation.getId());
      allocationDto.setAllocatedQuantity(allocation.getAllocatedQuantity());
      allocationDto.setAllocationPrice(allocation.getAllocationPrice());
      allocationDto.setAllocationStatus(allocation.getStatus().name());
      dto.setAllocation(allocationDto);
    } else {
      dto.setAllocation(null);
    }

    List<SaleSummaryDto> saleDtos =
        activeSales.stream()
            .map(
                sale -> {
                  SaleSummaryDto saleDto = new SaleSummaryDto();
                  saleDto.setId(sale.getId());
                  saleDto.setQuantity(sale.getQuantity());
                  saleDto.setAmount(sale.getAmount());
                  saleDto.setFee(sale.getFee());
                  saleDto.setTax(sale.getTax());
                  saleDto.setOtherCost(sale.getOtherCost());
                  saleDto.setStatus(sale.getStatus().name());
                  saleDto.setSoldAt(sale.getSaleDate().toString());
                  return saleDto;
                })
            .collect(Collectors.toList());
    dto.setSales(saleDtos);

    Long remainingQuantity = calculateRemainingQuantity(allocation, activeSales);
    dto.setRemainingQuantity(remainingQuantity);

    return dto;
  }

  private Long calculateRemainingQuantity(Allocation allocation, List<Sale> activeSales) {
    if (allocation == null) {
      return null;
    }
    long totalSoldQuantity = activeSales.stream().mapToLong(sale -> sale.getQuantity()).sum();
    return allocation.getAllocatedQuantity() - totalSoldQuantity;
  }

  /** 청약 목록을 투자 이력 DTO 목록으로 변환한다. */
  public List<InvestmentHistoryDto> toDtos(
      List<Subscription> subscriptions,
      Map<Long, Allocation> allocationMap,
      Map<Long, List<Sale>> saleMap,
      Map<Long, Ipo> ipoMap,
      Map<Long, String> investorNameMap,
      Map<Long, String> securitiesFirmNameMap) {
    return subscriptions.stream()
        .map(
            subscription -> {
              Allocation allocation = allocationMap.get(subscription.getId());
              List<Sale> sales = saleMap.get(subscription.getId());
              if (sales == null) {
                sales = List.of();
              }
              String investorName =
                  investorNameMap != null
                      ? investorNameMap.get(subscription.getInvestorId())
                      : null;
              String securitiesFirmName =
                  securitiesFirmNameMap != null
                      ? securitiesFirmNameMap.get(subscription.getSecuritiesAccountId())
                      : null;
              return toDto(
                  subscription, allocation, sales, ipoMap, investorName, securitiesFirmName);
            })
        .collect(Collectors.toList());
  }
}
