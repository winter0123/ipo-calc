package com.vibeaiteam.brokerintegration.service.rotation;

import com.vibeaiteam.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.vibeaiteam.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.vibeaiteam.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.vibeaiteam.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.vibeaiteam.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.vibeaiteam.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 암호문을 현재 키 버전으로 다시 암호화한다.
 *
 * <p>서버 암호화 키를 새 버전으로 바꿀 때 한 번 실행한다(기동 시 {@code APP_ENCRYPTION_ROTATE_ON_STARTUP=true}). 이미 현재 버전인
 * 값은 건드리지 않고, 복호화에 실패한 행은 건너뛴 채 개수만 센다(행 ID만 로그에 남기고 값은 남기지 않는다). 모든 값이 새 버전이 된 것을 확인한 뒤에 예전 키를
 * 지운다.
 *
 * <p>ponytail: 전체 행을 한 번에 읽는 개인용 규모 기준이다. 행이 수만 건이 되면 페이지 단위로 나눠야 한다.
 */
@Service
@RequiredArgsConstructor
public class EncryptionRotationService {

  private static final Logger log = LoggerFactory.getLogger(EncryptionRotationService.class);

  private final SecuritiesAccountApiCredentialRepository credentialRepository;
  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final BrokerSecretEncryptor brokerSecretEncryptor;
  private final AccountNumberEncryptor accountNumberEncryptor;

  /**
   * 재암호화 결과.
   *
   * @param credentials 다시 암호화한 증권사 인증정보 행 수
   * @param accounts 다시 암호화한 계좌번호 행 수
   * @param failures 복호화에 실패해 건너뛴 값의 수
   */
  public record Result(int credentials, int accounts, int failures) {}

  /**
   * 증권사 인증정보와 계좌번호를 모두 현재 키 버전으로 다시 암호화한다.
   *
   * @return 다시 암호화한 행 수와 실패 수
   */
  @Transactional
  public Result rotateAll() {
    int failures = 0;

    List<SecuritiesAccountApiCredential> changedCredentials = new ArrayList<>();
    for (SecuritiesAccountApiCredential credential : credentialRepository.findAll()) {
      boolean changed = false;
      try {
        String appKey = rotate(credential.getAppKeyEncrypted(), brokerSecretEncryptor);
        String appSecret = rotate(credential.getAppSecretEncrypted(), brokerSecretEncryptor);
        String token = rotate(credential.getAccessTokenEncrypted(), brokerSecretEncryptor);
        if (appKey != null) {
          credential.setAppKeyEncrypted(appKey);
          changed = true;
        }
        if (appSecret != null) {
          credential.setAppSecretEncrypted(appSecret);
          changed = true;
        }
        if (token != null) {
          credential.setAccessTokenEncrypted(token);
          changed = true;
        }
      } catch (RuntimeException e) {
        failures++;
        log.error("증권사 인증정보 재암호화 실패(건너뜀): credentialId={}", credential.getId());
        continue;
      }
      if (changed) {
        changedCredentials.add(credential);
      }
    }
    credentialRepository.saveAll(changedCredentials);

    List<SecuritiesAccount> changedAccounts = new ArrayList<>();
    for (SecuritiesAccount account : securitiesAccountRepository.findAll()) {
      try {
        String rotated = rotate(account.getAccountNumber(), accountNumberEncryptor);
        if (rotated != null) {
          account.setAccountNumber(rotated);
          changedAccounts.add(account);
        }
      } catch (RuntimeException e) {
        failures++;
        log.error("계좌번호 재암호화 실패(건너뜀): securitiesAccountId={}", account.getId());
      }
    }
    securitiesAccountRepository.saveAll(changedAccounts);

    log.info(
        "암호화 키 버전 재암호화 완료: credentials={}, accounts={}, failures={}",
        changedCredentials.size(),
        changedAccounts.size(),
        failures);
    return new Result(changedCredentials.size(), changedAccounts.size(), failures);
  }

  /** 현재 버전이 아니면 복호화해 현재 키로 다시 암호화한 값을, 건드릴 필요가 없으면 null을 돌려준다. */
  private static String rotate(String encrypted, BrokerSecretEncryptor encryptor) {
    return rotate(encrypted, encryptor::isCurrent, encryptor::decrypt, encryptor::encrypt);
  }

  private static String rotate(String encrypted, AccountNumberEncryptor encryptor) {
    return rotate(encrypted, encryptor::isCurrent, encryptor::decrypt, encryptor::encrypt);
  }

  private static String rotate(
      String encrypted,
      Predicate<String> isCurrent,
      Function<String, String> decrypt,
      Function<String, String> encrypt) {
    if (encrypted == null || encrypted.isBlank() || isCurrent.test(encrypted)) {
      return null;
    }
    return encrypt.apply(decrypt.apply(encrypted));
  }
}
