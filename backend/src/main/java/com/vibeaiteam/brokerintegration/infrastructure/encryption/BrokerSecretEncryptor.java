package com.vibeaiteam.brokerintegration.infrastructure.encryption;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 브로커 API 시크릿(앱키/앱시크릿/접근토큰) 암호화 유틸리티 (AES-256-GCM, JDK 내장 javax.crypto만 사용).
 *
 * <p>- AccountNumberEncryptor와 동일한 AES-256-GCM 구현이나, 별도 키(app.encryption.broker-key /
 * APP_ENCRYPTION_BROKER_KEY)를 주입받아 키 분리로 단일 키 유출 시 피해 범위를 제한한다. - encrypt(): 매 호출마다
 * 랜덤 12바이트 IV를 생성하고, 결과는 Base64(IV + ciphertext) 형태로 self-contained하게 반환한다
 * (decrypt 시 IV와 암호문을 분리해 복호화). - 키: 32바이트(256비트) 키를 Base64로 인코딩한 값을
 * application.yml(app.encryption.broker-key, 환경변수 APP_ENCRYPTION_BROKER_KEY)에서 주입받는다. 키가
 * 미설정 상태여도 빈 생성은 허용하고, 실제 encrypt()/decrypt() 호출 시점에 명확한 오류로 실패시킨다
 * (application.yml 의존 테스트의 컨텍스트 기동 방해 방지). - 예외 메시지에 평문·키 값을 절대 넣지 않는다.
 */
@Component
public class BrokerSecretEncryptor {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKey secretKey;

  /**
   * Base64로 인코딩된 암호화 키를 주입받아 생성한다.
   */
  public BrokerSecretEncryptor(@Value("${app.encryption.broker-key:}") String base64Key) {
    this.secretKey = (base64Key == null || base64Key.isBlank()) ? null : decodeKey(base64Key);
  }

  private static SecretKey decodeKey(String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException(
          "브로커 암호화 키(app.encryption.broker-key/APP_ENCRYPTION_BROKER_KEY)는 "
              + "32바이트(256비트) 키를 Base64로 인코딩한 값이어야 합니다.");
    }
    return new SecretKeySpec(keyBytes, ALGORITHM);
  }

  private SecretKey getSecretKey() {
    if (secretKey == null) {
      throw new IllegalStateException(
          "브로커 암호화 키가 설정되지 않았습니다. "
              + "APP_ENCRYPTION_BROKER_KEY(또는 app.encryption.broker-key)에 "
              + "32바이트 키의 Base64 값을 지정하세요.");
    }
    return secretKey;
  }

  /**
   * 평문을 AES-GCM으로 암호화해 Base64 문자열로 반환한다.
   *
   * @param plainText 평문 시크릿
   * @return Base64(IV + ciphertext) 형태의 암호문
   * @throws IllegalStateException 키가 설정되지 않았을 때
   */
  public String encrypt(String plainText) {
    getSecretKey(); // 키 미설정 시 조기 실패
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("브로커 시크릿 암호화에 실패했습니다.", e);
    }
  }

  /**
   * Base64 암호문을 복호화해 평문을 반환한다.
   *
   * @param encryptedText Base64(IV + ciphertext) 형태의 암호문
   * @return 복호화된 평문
   * @throws IllegalStateException 키가 설정되지 않았을 때
   */
  public String decrypt(String encryptedText) {
    getSecretKey(); // 키 미설정 시 조기 실패
    try {
      byte[] combined = Base64.getDecoder().decode(encryptedText);
      byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
      byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] plainText = cipher.doFinal(cipherText);
      return new String(plainText, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("브로커 시크릿 복호화에 실패했습니다.", e);
    }
  }

  /**
   * 암호화 키가 설정되어 있는지 확인한다.
   *
   * <p>SF-2b 등록 API에서 키 미설정 시 명확한 오류로 실패하기 위해 사용한다.
   *
   * @return 키가 설정되어 있으면 true, 아니면 false
   */
  public boolean isConfigured() {
    return secretKey != null;
  }
}