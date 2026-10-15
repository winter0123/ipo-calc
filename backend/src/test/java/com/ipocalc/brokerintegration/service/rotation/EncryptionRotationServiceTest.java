package com.ipocalc.brokerintegration.service.rotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ipocalc.brokerintegration.domain.model.credential.SecuritiesAccountApiCredential;
import com.ipocalc.brokerintegration.infrastructure.encryption.BrokerSecretEncryptor;
import com.ipocalc.brokerintegration.repository.credential.SecuritiesAccountApiCredentialRepository;
import com.ipocalc.securitiesaccountmanagement.domain.model.securitiesaccount.SecuritiesAccount;
import com.ipocalc.securitiesaccountmanagement.infrastructure.encryption.AccountNumberEncryptor;
import com.ipocalc.securitiesaccountmanagement.repository.securitiesaccount.SecuritiesAccountRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class EncryptionRotationServiceTest {

  private final SecuritiesAccountApiCredentialRepository credentialRepository =
      mock(SecuritiesAccountApiCredentialRepository.class);
  private final SecuritiesAccountRepository accountRepository =
      mock(SecuritiesAccountRepository.class);

  private static String newKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void reEncryptsOldVersionValuesSkipsCurrentOnesAndCountsFailures() {
    String brokerK1 = newKey();
    String accountK1 = newKey();
    BrokerSecretEncryptor oldBroker = new BrokerSecretEncryptor(brokerK1);
    AccountNumberEncryptor oldAccount = new AccountNumberEncryptor(accountK1);
    BrokerSecretEncryptor broker = new BrokerSecretEncryptor(brokerK1, "v2:" + newKey(), "");
    AccountNumberEncryptor account = new AccountNumberEncryptor(accountK1, "v2:" + newKey(), "");

    SecuritiesAccountApiCredential old =
        SecuritiesAccountApiCredential.builder()
            .id(1L)
            .appKeyEncrypted(oldBroker.encrypt("old-key"))
            .appSecretEncrypted(oldBroker.encrypt("old-secret"))
            .accessTokenEncrypted(oldBroker.encrypt("old-token"))
            .build();
    SecuritiesAccountApiCredential current =
        SecuritiesAccountApiCredential.builder()
            .id(2L)
            .appKeyEncrypted(broker.encrypt("new-key"))
            .appSecretEncrypted(broker.encrypt("new-secret"))
            .build();
    SecuritiesAccountApiCredential broken =
        SecuritiesAccountApiCredential.builder()
            .id(3L)
            .appKeyEncrypted("v1:AAAAAAAAAAAAAAAAAAAAAAAA")
            .appSecretEncrypted("x")
            .build();
    SecuritiesAccount oldAccountRow =
        SecuritiesAccount.builder()
            .id(10L)
            .accountNumber(oldAccount.encrypt("123-456-789"))
            .build();
    SecuritiesAccount currentAccountRow =
        SecuritiesAccount.builder().id(11L).accountNumber(account.encrypt("999-888-777")).build();
    when(credentialRepository.findAll()).thenReturn(List.of(old, current, broken));
    when(accountRepository.findAll()).thenReturn(List.of(oldAccountRow, currentAccountRow));

    EncryptionRotationService.Result result =
        new EncryptionRotationService(credentialRepository, accountRepository, broker, account)
            .rotateAll();

    assertThat(result.credentials()).isEqualTo(1);
    assertThat(result.accounts()).isEqualTo(1);
    assertThat(result.failures()).isEqualTo(1);
    assertThat(old.getAppKeyEncrypted()).startsWith("v2:");
    assertThat(broker.decrypt(old.getAppKeyEncrypted())).isEqualTo("old-key");
    assertThat(broker.decrypt(old.getAppSecretEncrypted())).isEqualTo("old-secret");
    assertThat(broker.decrypt(old.getAccessTokenEncrypted())).isEqualTo("old-token");
    assertThat(account.decrypt(oldAccountRow.getAccountNumber())).isEqualTo("123-456-789");
    assertThat(oldAccountRow.getAccountNumber()).startsWith("v2:");
    verify(credentialRepository).saveAll(List.of(old));
    verify(accountRepository).saveAll(List.of(oldAccountRow));
  }

  @Test
  void whenEverythingIsCurrentNothingIsSaved() {
    String k = newKey();
    BrokerSecretEncryptor broker = new BrokerSecretEncryptor(k);
    AccountNumberEncryptor account = new AccountNumberEncryptor(k);
    when(credentialRepository.findAll())
        .thenReturn(
            List.of(
                SecuritiesAccountApiCredential.builder()
                    .id(1L)
                    .appKeyEncrypted(broker.encrypt("a"))
                    .appSecretEncrypted(broker.encrypt("b"))
                    .build()));
    when(accountRepository.findAll()).thenReturn(List.of());

    EncryptionRotationService.Result result =
        new EncryptionRotationService(credentialRepository, accountRepository, broker, account)
            .rotateAll();

    assertThat(result).isEqualTo(new EncryptionRotationService.Result(0, 0, 0));
    verify(credentialRepository).saveAll(List.<SecuritiesAccountApiCredential>of());
    org.mockito.Mockito.verify(accountRepository).saveAll(anyList());
  }
}
