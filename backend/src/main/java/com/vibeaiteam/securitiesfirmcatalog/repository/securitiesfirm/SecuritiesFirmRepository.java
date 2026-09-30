package com.vibeaiteam.securitiesfirmcatalog.repository.securitiesfirm;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.securitiesfirmcatalog.domain.model.securitiesfirm.SecuritiesFirm;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 증권사 JPA 저장소.
 */
@Repository
public interface SecuritiesFirmRepository extends JpaRepository<SecuritiesFirm, Long> {

  /**
   * 이름 · 상태 조건으로 증권사를 단건 조회한다.
   */
  Optional<SecuritiesFirm> findByNameAndStatus(String name, RecordStatus status);

  /**
   * 삭제되지 않은 증권사를 목록 조회한다.
   */
  List<SecuritiesFirm> findByDeletedAtIsNull();

  /**
   * ID 조건(삭제되지 않은 건만)으로 증권사를 단건 조회한다.
   */
  Optional<SecuritiesFirm> findByIdAndDeletedAtIsNull(Long id);
}
