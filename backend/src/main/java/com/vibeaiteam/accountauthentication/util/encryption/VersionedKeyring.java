package com.vibeaiteam.accountauthentication.util.encryption;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 버전이 붙은 AES-256-GCM 키 묶음.
 *
 * <p>암호문은 {@code v<번호>:Base64(IV + ciphertext)} 형태이고, 접두사가 없는 예전 암호문은 {@code v1}로 본다. 암호화는 항상 현재 키로
 * 하고 복호화는 암호문의 버전에 맞는 키로 하므로, 새 키를 추가해도 예전 암호문을 그대로 읽을 수 있다. 예전 키는 모든 암호문을 새 버전으로 다시 암호화한 뒤에 지운다.
 *
 * <p>키 지정: 기존 단일 키(legacy)는 {@code v1}로 등록되고, 목록({@code v2:Base64,v3:Base64})으로 키를 더 둘 수 있다. 현재 버전을
 * 따로 주지 않으면 번호가 가장 큰 버전을 쓴다. 예외 메시지에는 평문·키 값을 넣지 않는다.
 */
public final class VersionedKeyring {

  /**
   * 호출한 쪽(계좌번호·브로커 시크릿)별 오류 문구.
   *
   * @param unconfigured 키가 하나도 없을 때
   * @param invalidKey 키 길이·형식이 틀렸을 때
   * @param encryptFailed 암호화 실패
   * @param decryptFailed 복호화 실패
   */
  public record Messages(
      String unconfigured, String invalidKey, String encryptFailed, String decryptFailed) {}

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String LEGACY_VERSION = "v1";
  private static final Pattern VERSION_PREFIX = Pattern.compile("^(v\\d+):(.*)$", Pattern.DOTALL);
  private static final Pattern VERSION_NAME = Pattern.compile("^v\\d+$");
  private static final int IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final Map<String, SecretKey> keys = new LinkedHashMap<>();
  private final String currentVersion;
  private final Messages messages;

  /**
   * 키 묶음을 만든다.
   *
   * @param legacyBase64Key 기존 단일 키(없으면 빈 값). {@code v1}로 등록된다
   * @param keysSpec 추가 키 목록 {@code v2:Base64,v3:Base64}(없으면 빈 값)
   * @param currentVersion 암호화에 쓸 버전(없으면 가장 큰 번호)
   * @param messages 오류 문구
   */
  public VersionedKeyring(
      String legacyBase64Key, String keysSpec, String currentVersion, Messages messages) {
    this.messages = messages;
    if (legacyBase64Key != null && !legacyBase64Key.isBlank()) {
      keys.put(LEGACY_VERSION, decodeKey(legacyBase64Key.trim()));
    }
    if (keysSpec != null && !keysSpec.isBlank()) {
      for (String entry : keysSpec.split(",")) {
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
          continue;
        }
        int colon = trimmed.indexOf(':');
        String version = colon < 0 ? "" : trimmed.substring(0, colon).trim();
        if (!VERSION_NAME.matcher(version).matches()) {
          throw new IllegalArgumentException("키 목록 항목은 v<번호>:<Base64 키> 형식이어야 합니다.");
        }
        if (keys.containsKey(version)) {
          throw new IllegalArgumentException("키 버전이 중복되었습니다: " + version);
        }
        keys.put(version, decodeKey(trimmed.substring(colon + 1).trim()));
      }
    }
    this.currentVersion = resolveCurrent(currentVersion);
  }

  private String resolveCurrent(String requested) {
    if (keys.isEmpty()) {
      return null;
    }
    if (requested != null && !requested.isBlank()) {
      String version = requested.trim();
      if (!keys.containsKey(version)) {
        throw new IllegalArgumentException("현재 키 버전이 키 목록에 없습니다: " + version);
      }
      return version;
    }
    return keys.keySet().stream()
        .max(Comparator.comparingInt(VersionedKeyring::number))
        .orElseThrow();
  }

  private static int number(String version) {
    return Integer.parseInt(version.substring(1));
  }

  private SecretKey decodeKey(String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException(messages.invalidKey());
    }
    return new SecretKeySpec(keyBytes, ALGORITHM);
  }

  /**
   * 키가 하나라도 설정되어 있는지 확인한다.
   *
   * @return 키가 있으면 true
   */
  public boolean isConfigured() {
    return !keys.isEmpty();
  }

  /**
   * 암호화에 쓰는 현재 키 버전을 돌려준다.
   *
   * @return 현재 버전(키가 없으면 null)
   */
  public String currentVersion() {
    return currentVersion;
  }

  /**
   * 평문을 현재 키로 암호화한다.
   *
   * @param plainText 평문
   * @return {@code v<번호>:Base64(IV + ciphertext)}
   * @throws IllegalStateException 키가 없거나 암호화에 실패했을 때
   */
  public String encrypt(String plainText) {
    requireConfigured();
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          keys.get(currentVersion),
          new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return currentVersion + ":" + Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(messages.encryptFailed(), e);
    }
  }

  /**
   * 암호문을 버전에 맞는 키로 복호화한다.
   *
   * @param encryptedText {@code v<번호>:Base64(...)} 또는 접두사 없는 예전 암호문(v1)
   * @return 평문
   * @throws IllegalStateException 키가 없거나 알 수 없는 버전이거나 복호화에 실패했을 때
   */
  public String decrypt(String encryptedText) {
    requireConfigured();
    String version = versionOf(encryptedText);
    SecretKey key = keys.get(version);
    if (key == null) {
      throw new IllegalStateException(messages.decryptFailed() + " (알 수 없는 키 버전: " + version + ")");
    }
    try {
      byte[] combined = Base64.getDecoder().decode(payloadOf(encryptedText));
      byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
      byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(messages.decryptFailed(), e);
    }
  }

  /**
   * 암호문이 현재 키 버전으로 암호화된 것인지 확인한다.
   *
   * @param encryptedText 암호문
   * @return 현재 버전이면 true(접두사 없는 예전 암호문은 v1)
   */
  public boolean isCurrent(String encryptedText) {
    return currentVersion != null && currentVersion.equals(versionOf(encryptedText));
  }

  private static String versionOf(String encryptedText) {
    Matcher matcher = VERSION_PREFIX.matcher(encryptedText);
    return matcher.matches() ? matcher.group(1) : LEGACY_VERSION;
  }

  private static String payloadOf(String encryptedText) {
    Matcher matcher = VERSION_PREFIX.matcher(encryptedText);
    return matcher.matches() ? matcher.group(2) : encryptedText;
  }

  private void requireConfigured() {
    if (keys.isEmpty()) {
      throw new IllegalStateException(messages.unconfigured());
    }
  }
}
