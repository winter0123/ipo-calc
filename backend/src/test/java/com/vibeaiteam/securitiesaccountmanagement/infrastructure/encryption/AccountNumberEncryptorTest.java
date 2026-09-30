package com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.accountauthentication.AccountAuthenticationApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * AC 인용: docs/architecture/securities-account-management.md (계좌번호 암호화 저장) - D-SA-001: AES-256-GCM,
 * JDK 내장 javax.crypto 사용 - encrypt()는 계좌번호를 암호화하고, decrypt()는 원문을 복원한다 - 매 encrypt()마다 랜덤 IV를 생성해
 * 같은 평문도 서로 다른 암호문이 나온다.
 */
@SpringBootTest
@ContextConfiguration(classes = AccountAuthenticationApplication.class)
@ActiveProfiles("test")
class AccountNumberEncryptorTest {

  @Autowired private AccountNumberEncryptor accountNumberEncryptor;

  private static final String PLAIN_ACCOUNT_NUMBER = "123-456-789012";

  @Test
  void decryptReturnsOriginalPlainTextAfterEncrypt() {
    // Given: 평문 계좌번호가 존재함

    // When: encrypt 후 decrypt 수행
    String encrypted = accountNumberEncryptor.encrypt(PLAIN_ACCOUNT_NUMBER);
    String decrypted = accountNumberEncryptor.decrypt(encrypted);

    // Then: 복호화 결과가 원문과 같고, 암호문은 평문과 다르다
    assertThat(encrypted).isNotEqualTo(PLAIN_ACCOUNT_NUMBER);
    assertThat(decrypted).isEqualTo(PLAIN_ACCOUNT_NUMBER);
  }

  @Test
  void encryptProducesDifferentCipherTextForSamePlainText() {
    // When: 같은 평문을 두 번 암호화
    String first = accountNumberEncryptor.encrypt(PLAIN_ACCOUNT_NUMBER);
    String second = accountNumberEncryptor.encrypt(PLAIN_ACCOUNT_NUMBER);

    // Then: 매 암호화마다 랜덤 IV가 생성되어 암호문이 서로 다르다
    assertThat(first).isNotEqualTo(second);
    assertThat(accountNumberEncryptor.decrypt(first)).isEqualTo(PLAIN_ACCOUNT_NUMBER);
    assertThat(accountNumberEncryptor.decrypt(second)).isEqualTo(PLAIN_ACCOUNT_NUMBER);
  }

  @Test
  void encryptDecryptHandlesVariousAccountNumberFormats() {
    // Given: 다양한 형식의 계좌번호 후보
    String[] samples = {
      "123-456-789012", "1", "9876543210", "0123-4567-8901-2345", "계좌번호-숫자-하이픈-조합"
    };

    // When & Then: 모든 샘플이 encrypt → decrypt 시 원문과 동일해야 한다
    for (String sample : samples) {
      String encrypted = accountNumberEncryptor.encrypt(sample);
      assertThat(accountNumberEncryptor.decrypt(encrypted)).isEqualTo(sample);
    }
  }
}
