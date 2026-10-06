package com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption;

import com.vibeaiteam.accountauthentication.util.encryption.VersionedKeyring;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 계좌번호 암호화 유틸리티 (AES-256-GCM, JDK 내장 javax.crypto만 사용, D-SA-001).
 *
 * <p>키는 버전 관리된다({@link VersionedKeyring}): 기존 단일 키(app.encryption.key / APP_ENCRYPTION_KEY)는 v1이고,
 * {@code APP_ENCRYPTION_KEYS}(v2:Base64,...)로 새 키를 추가하면 새 키로 암호화하면서 예전 암호문도 읽는다. 키가 미설정 상태여도 빈 생성은
 * 허용하고, 실제 encrypt()/decrypt() 호출 시점에 명확한 오류로 실패시킨다. 예외 메시지에 평문·키 값을 넣지 않는다.
 */
@Component
public class AccountNumberEncryptor {

  private static final VersionedKeyring.Messages MESSAGES =
      new VersionedKeyring.Messages(
          "암호화 키가 설정되지 않았습니다. "
              + "APP_ENCRYPTION_KEY(또는 app.encryption.key)에 32바이트 키의 Base64 값을 지정하세요.",
          "암호화 키(app.encryption.key/APP_ENCRYPTION_KEY)는 32바이트(256비트) 키를 Base64로 인코딩한 값이어야 합니다.",
          "계좌번호 암호화에 실패했습니다.",
          "계좌번호 복호화에 실패했습니다.");

  private final VersionedKeyring keyring;

  /**
   * 키 설정을 주입받아 생성한다.
   *
   * @param legacyKey 기존 단일 키(v1, Base64)
   * @param keys 추가 키 목록(v2:Base64,...)
   * @param current 암호화에 쓸 버전(없으면 가장 큰 번호)
   */
  @Autowired
  public AccountNumberEncryptor(
      @Value("${app.encryption.key:}") String legacyKey,
      @Value("${app.encryption.keys:}") String keys,
      @Value("${app.encryption.key-current:}") String current) {
    this.keyring = new VersionedKeyring(legacyKey, keys, current, MESSAGES);
  }

  /**
   * 단일 키(v1)만으로 생성한다.
   *
   * @param base64Key Base64로 인코딩한 32바이트 키(없으면 빈 값)
   */
  public AccountNumberEncryptor(String base64Key) {
    this(base64Key, "", "");
  }

  /**
   * 평문을 현재 키로 암호화한다.
   *
   * @param plainText 평문 계좌번호
   * @return {@code v<번호>:Base64(IV + ciphertext)} 형태의 암호문
   * @throws IllegalStateException 키가 설정되지 않았을 때
   */
  public String encrypt(String plainText) {
    return keyring.encrypt(plainText);
  }

  /**
   * 암호문을 버전에 맞는 키로 복호화한다.
   *
   * @param encryptedText 암호문(접두사 없는 예전 암호문은 v1)
   * @return 복호화된 평문
   * @throws IllegalStateException 키가 설정되지 않았거나 복호화에 실패했을 때
   */
  public String decrypt(String encryptedText) {
    return keyring.decrypt(encryptedText);
  }

  /**
   * 암호화 키가 설정되어 있는지 확인한다.
   *
   * @return 키가 설정되어 있으면 true, 아니면 false
   */
  public boolean isConfigured() {
    return keyring.isConfigured();
  }

  /**
   * 암호문이 현재 키 버전으로 암호화된 것인지 확인한다.
   *
   * @param encryptedText 암호문
   * @return 현재 버전이면 true
   */
  public boolean isCurrent(String encryptedText) {
    return keyring.isCurrent(encryptedText);
  }
}
