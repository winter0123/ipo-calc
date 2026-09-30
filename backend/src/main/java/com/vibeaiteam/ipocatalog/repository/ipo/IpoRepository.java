package com.vibeaiteam.ipocatalog.repository.ipo;

import com.vibeaiteam.ipocatalog.domain.model.ipo.Ipo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 공모주 JPA 저장소.
 */
@Repository
public interface IpoRepository extends JpaRepository<Ipo, Long> {

  /**
   * ID 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다.
   */
  Optional<Ipo> findByIdAndDeletedAtIsNull(Long id);

  /**
   * 삭제되지 않은 공모주를 목록 조회한다(생성일 내림차순).
   */
  List<Ipo> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

  /**
   * 종목코드 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다.
   */
  Optional<Ipo> findByStockCodeAndStatusAndDeletedAtIsNull(String stockCode, String status);

  /**
   * 법인코드 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다.
   */
  Optional<Ipo> findByCorpCodeAndStatusAndDeletedAtIsNull(String corpCode, String status);

  /**
   * 공모주명 · 청약 시작일 · 상태 조건(삭제되지 않은 건만)으로 공모주를 단건 조회한다.
   */
  Optional<Ipo> findByIpoNameAndSubscriptionStartDateAndStatusAndDeletedAtIsNull(
      String ipoName, LocalDate subscriptionStartDate, String status);
}
