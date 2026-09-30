package com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption;

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
 * 계좌번호 암호화 유틸리티 (AES-256-GCM, JDK 내장 javax.crypto만 사용).
 *
 * <p>- D-SA-001: AES-256-GCM, 외부 라이브러리 추가 없음 - encrypt(): 매 호출마다 랜덤 12바이트 IV를 생성하고, 결과는 Base64(IV +
 * ciphertext) 형태로 self-contained하게 반환한다 (decrypt 시 IV와 암호문을 분리해 복호화). - 키: 32바이트(256비트) 키를 Base64로
 * 인코딩한 값을 application.yml(app.encryption.key, 환경변수 APP_ENCRYPTION_KEY)에서 주입받는다. 키가 미설정 상태여도 빈 생성은
 * 허용하고, 실제 encrypt()/decrypt() 호출 시점에 명확한 오류로 실패시킨다 (application.yml 의존 테스트의 컨텍스트 기동 방해 방지).
 */
@Component
public class AccountNumberEncryptor {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKey secretKey;

  /**
   * Base64로 인코딩된 암호화 키를 주입받아 생성한다.
   */
  public AccountNumberEncryptor(@Value("${app.encryption.key:}") String base64Key) {
    this.secretKey = (base64Key == null || base64Key.isBlank()) ? null : decodeKey(base64Key);
  }

  private static SecretKey decodeKey(String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException(
          "암호화 키(app.encryption.key/APP_ENCRYPTION_KEY)는 32바이트(256비트) 키를 Base64로 인코딩한 값이어야 합니다.");
    }
    return new SecretKeySpec(keyBytes, ALGORITHM);
  }

  private SecretKey getSecretKey() {
    if (secretKey == null) {
      throw new IllegalStateException(
          "암호화 키가 설정되지 않았습니다. "
              + "APP_ENCRYPTION_KEY(또는 app.encryption.key)에 32바이트 키의 Base64 값을 지정하세요.");
    }
    return secretKey;
  }

  /**
   * 평문을 AES-GCM으로 암호화해 Base64 문자열로 반환한다.
   */
  public String encrypt(String plainText) {
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
      throw new IllegalStateException("계좌번호 암호화에 실패했습니다.", e);
    }
  }

  /**
   * Base64 암호문을 복호화해 평문을 반환한다.
   */
  public String decrypt(String encryptedText) {
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
      throw new IllegalStateException("계좌번호 복호화에 실패했습니다.", e);
    }
  }
}
