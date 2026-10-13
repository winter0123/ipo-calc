package com.vibeaiteam.brokerintegration.repository.credential;

import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
          Long securitiesAccountId, String apiProvider);

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

  /**
   * 계좌 ID로 삭제되지 않은 인증정보를 최신 갱신 순으로 조회한다.
   *
   * @param securitiesAccountId 증권계좌 ID
   * @return 활성 인증정보 목록 (없으면 빈 목록)
   */
  List<SecuritiesAccountApiCredential>
      findBySecuritiesAccountIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long securitiesAccountId);

  /**
   * 계좌 ID로 삭제되지 않은 인증정보 목록을 조회한다.
   *
   * @param securitiesAccountId 증권계좌 ID
   * @return 활성 인증정보 목록 (없으면 빈 목록)
   */
  List<SecuritiesAccountApiCredential> findBySecuritiesAccountIdAndDeletedAtIsNull(
      Long securitiesAccountId);

  /**
   * 증권계좌의 인증정보를 모두 물리 삭제한다. 비밀키 암호문을 남기지 않기 위해 연결 해제 때 쓴다.
   *
   * @param securitiesAccountId 대상 증권계좌 ID
   */
  void deleteBySecuritiesAccountId(Long securitiesAccountId);

  /**
   * 토큰 컬럼만 갱신한다. 읽을 때의 앱키 암호문과 같을 때만 반영해, 그 사이 교체된 새 키를 덮어쓰지 않는다.
   *
   * @param id 인증정보 ID
   * @param appKeyEncrypted 토큰 발급에 쓴 앱키 암호문
   * @param tokenEncrypted 암호화한 접근토큰
   * @param expiresAt 토큰 만료 시각
   * @param now 수정 시각
   * @return 갱신된 행 수(키가 바뀌었거나 삭제됐으면 0)
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE SecuritiesAccountApiCredential c SET c.accessTokenEncrypted = :token,"
          + " c.tokenExpiresAt = :expiresAt, c.updatedAt = :now"
          + " WHERE c.id = :id AND c.appKeyEncrypted = :appKey AND c.deletedAt IS NULL")
  int updateTokenIfKeyUnchanged(
      @Param("id") Long id,
      @Param("appKey") String appKeyEncrypted,
      @Param("token") String tokenEncrypted,
      @Param("expiresAt") LocalDateTime expiresAt,
      @Param("now") LocalDateTime now);
}
