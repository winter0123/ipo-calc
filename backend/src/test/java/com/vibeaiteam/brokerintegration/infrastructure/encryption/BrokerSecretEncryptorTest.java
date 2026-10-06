package com.vibeaiteam.brokerintegration.infrastructure.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/breakdown/broker-readonly-api-pilot.md (SF-2) - AC-1810: 시크릿이 암호화되어 저장되고 DB 직접 조회해도
 * 평문이 보이지 않는다 - AC-1814(하위 절반): APP_ENCRYPTION_BROKER_KEY 미설정 시 encrypt가 명확한 오류로 실패한다 -
 * BrokerSecretEncryptor는 AccountNumberEncryptor와 동일한 AES-256-GCM 구현이되, 별도
 * 키(app.encryption.broker-key / APP_ENCRYPTION_BROKER_KEY)를 주입받아 키 분리로 단일 키 유출 시 피해 범위를 제한한다.
 */
@SpringBootTest
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
@ActiveProfiles("test")
class BrokerSecretEncryptorTest {

  @Autowired private BrokerSecretEncryptor brokerSecretEncryptor;

  private static final String PLAIN_SECRET = "test-app-secret-12345";

  @Test
  @DisplayName("암복호화 왕복: encrypt 후 decrypt 시 원문이 복원된다")
  void decryptReturnsOriginalPlainTextAfterEncrypt() {
    // Given: 평문 시크릿이 존재함

    // When: encrypt 후 decrypt 수행
    String encrypted = brokerSecretEncryptor.encrypt(PLAIN_SECRET);
    String decrypted = brokerSecretEncryptor.decrypt(encrypted);

    // Then: 복호화 결과가 원문과 같고, 암호문은 평문과 다르다
    assertThat(encrypted).isNotEqualTo(PLAIN_SECRET);
    assertThat(decrypted).isEqualTo(PLAIN_SECRET);
  }

  @Test
  @DisplayName("같은 평문 2회 암호화 시 IV가 달라 암호문이 서로 다르다")
  void encryptProducesDifferentCipherTextForSamePlainText() {
    // When: 같은 평문을 두 번 암호화
    String first = brokerSecretEncryptor.encrypt(PLAIN_SECRET);
    String second = brokerSecretEncryptor.encrypt(PLAIN_SECRET);

    // Then: 매 암호화마다 랜덤 IV가 생성되어 암호문이 서로 다르다
    assertThat(first).isNotEqualTo(second);
    assertThat(brokerSecretEncryptor.decrypt(first)).isEqualTo(PLAIN_SECRET);
    assertThat(brokerSecretEncryptor.decrypt(second)).isEqualTo(PLAIN_SECRET);
  }

  @Test
  @DisplayName("다양한 시크릿 형식(앱키/앱시크릿/토큰 형태)을 모두 암복호화할 수 있다")
  void encryptDecryptHandlesVariousSecretFormats() {
    // Given: 다양한 형식의 시크릿 후보
    String[] samples = {
      "app-key-1234567890abcdef",
      "app-secret-abcdefghijklmnopqrstuvwx",
      "access-token-xyz789",
      "very-long-secret-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?"
    };

    // When & Then: 모든 샘플이 encrypt → decrypt 시 원문과 동일해야 한다
    for (String sample : samples) {
      String encrypted = brokerSecretEncryptor.encrypt(sample);
      assertThat(brokerSecretEncryptor.decrypt(encrypted)).isEqualTo(sample);
    }
  }

  @Test
  @DisplayName("키 미설정 시 encrypt 호출하면 IllegalStateException 발생")
  void encryptThrowsExceptionWhenKeyNotConfigured() {
    // Given: 키가 설정되지 않은 BrokerSecretEncryptor 인스턴스
    BrokerSecretEncryptor encryptorWithoutKey = new BrokerSecretEncryptor("");

    // When & Then: encrypt 호출 시 명확한 오류로 실패
    assertThatThrownBy(() -> encryptorWithoutKey.encrypt(PLAIN_SECRET))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("브로커 암호화 키가 설정되지 않았습니다");
  }

  @Test
  @DisplayName("키 미설정 시 decrypt 호출하면 IllegalStateException 발생")
  void decryptThrowsExceptionWhenKeyNotConfigured() {
    // Given: 키가 설정되지 않은 BrokerSecretEncryptor 인스턴스
    BrokerSecretEncryptor encryptorWithoutKey = new BrokerSecretEncryptor("");

    // When & Then: decrypt 호출 시 명확한 오류로 실패
    assertThatThrownBy(() -> encryptorWithoutKey.decrypt("dummy-ciphertext"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("브로커 암호화 키가 설정되지 않았습니다");
  }

  @Test
  @DisplayName("키 미설정 시 isConfigured는 false를 반환한다")
  void isConfiguredReturnsFalseWhenKeyNotConfigured() {
    // Given: 키가 설정되지 않은 BrokerSecretEncryptor 인스턴스
    BrokerSecretEncryptor encryptorWithoutKey = new BrokerSecretEncryptor("");

    // When & Then: isConfigured()가 false 반환
    assertThat(encryptorWithoutKey.isConfigured()).isFalse();
  }

  @Test
  @DisplayName("키 설정 시 isConfigured는 true를 반환한다")
  void isConfiguredReturnsTrueWhenKeyConfigured() {
    // When & Then: 주입받은 encryptor는 키가 설정되어 있음
    assertThat(brokerSecretEncryptor.isConfigured()).isTrue();
  }

  @Test
  @DisplayName("32바이트가 아닌 키로 생성 시 IllegalArgumentException 발생")
  void constructorThrowsExceptionForNon32ByteKey() {
    // Given: 32바이트가 아닌 Base64 키 (16바이트 = 128비트)
    String shortKeyBase64 = "MTIzNDU2Nzg5MDEyMzQ1Ng=="; // "1234567890123456" Base64

    // When & Then: 생성자에서 예외 발생
    assertThatThrownBy(() -> new BrokerSecretEncryptor(shortKeyBase64))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32바이트(256비트) 키를 Base64로 인코딩한 값이어야 합니다");
  }

  @Test
  @DisplayName("계좌번호용 키(app.encryption.key)로는 브로커 시크릿을 복호화할 수 없다(키 분리 확인)")
  void cannotDecryptWithAccountNumberEncryptorKey() {
    // Given: AccountNumberEncryptor 키와 다른 키로 각각 생성한 두 Encryptor 인스턴스
    // AccountNumberEncryptor 테스트용 키 (application-test.yml의 key)
    String accountKeyBase64 = "pQSZosg8xShWKKLtxE0Yk/fF4GOaG7DPB2fizcRDHnc=";
    // BrokerSecretEncryptor 테스트용 키 (application-test.yml의 broker-key)
    String brokerKeyBase64 = "DKN+EauX5IPvKhIvOAlCNtBUTmXV1rm+pY/A+IEeHIQ=";

    BrokerSecretEncryptor accountEncryptor = new BrokerSecretEncryptor(accountKeyBase64);
    BrokerSecretEncryptor brokerEncryptor = new BrokerSecretEncryptor(brokerKeyBase64);

    // When: brokerEncryptor로 암호화
    String encrypted = brokerEncryptor.encrypt(PLAIN_SECRET);

    // Then: accountEncryptor로는 복호화 실패 (키가 다르므로 GCM 태그 검증 실패)
    assertThatThrownBy(() -> accountEncryptor.decrypt(encrypted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("브로커 시크릿 복호화에 실패했습니다");

    // 반대 방향도 동일
    String encryptedWithAccountKey = accountEncryptor.encrypt(PLAIN_SECRET);
    assertThatThrownBy(() -> brokerEncryptor.decrypt(encryptedWithAccountKey))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("브로커 시크릿 복호화에 실패했습니다");
  }
}
