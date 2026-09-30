package com.vibeaiteam.brokerintegration.repository.credential;

import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 증권계좌 API 인증정보 리포지터리.
 *
 * <p>소프트 삭제·소유 확인에 필요한 최소 조회만 제공한다.
 */
@Repository
public interface SecuritiesAccountApiCredentialRepository
    extends JpaRepository<SecuritiesAccountApiCredential, Long> {

  /**
   * 계좌 ID와 API 제공자로 활성(삭제되지 않은) 인증정보를 조회한다.
   *
   * @param securitiesAccountId 증권계좌 ID
   * @param apiProvider API 제공자 식별자 (예: KIS)
   * @return 활성 인증정보 (없으면 empty)
   */
  Optional<SecuritiesAccountApiCredential>
      findBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
          Long securitiesAccountId,
          String apiProvider);

  /**
   * 계좌 ID와 API 제공자로 활성(삭제되지 않은) 인증정보가 존재하는지 확인한다.
   *
   * @param securitiesAccountId 증권계좌 ID
   * @param apiProvider API 제공자 식별자 (예: KIS)
   * @return 존재하면 true
   */
  boolean existsBySecuritiesAccountIdAndApiProviderAndDeletedAtIsNull(
      Long securitiesAccountId, String apiProvider);

  /**
   * ID로 삭제되지 않은 인증정보를 조회한다.
   *
   * @param id 인증정보 ID
   * @return 활성 인증정보 (없으면 empty)
   */
  Optional<SecuritiesAccountApiCredential> findByIdAndDeletedAtIsNull(Long id);
}