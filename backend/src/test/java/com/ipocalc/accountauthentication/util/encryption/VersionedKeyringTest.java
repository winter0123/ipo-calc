package com.ipocalc.accountauthentication.util.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class VersionedKeyringTest {

  private static final VersionedKeyring.Messages MESSAGES =
      new VersionedKeyring.Messages("키 없음", "키 형식 오류", "암호화 실패", "복호화 실패");

  private static String newKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void legacySingleKeyIsVersionOneAndEncryptsWithPrefix() {
    VersionedKeyring ring = new VersionedKeyring(newKey(), "", "", MESSAGES);

    String encrypted = ring.encrypt("secret");

    assertThat(encrypted).startsWith("v1:");
    assertThat(ring.decrypt(encrypted)).isEqualTo("secret");
    assertThat(ring.currentVersion()).isEqualTo("v1");
  }

  @Test
  void ciphertextWithoutPrefixIsTreatedAsVersionOne() {
    VersionedKeyring ring = new VersionedKeyring(newKey(), "", "", MESSAGES);
    String legacyFormat = ring.encrypt("secret").substring("v1:".length());

    assertThat(ring.decrypt(legacyFormat)).isEqualTo("secret");
    assertThat(ring.isCurrent(legacyFormat)).isTrue();
  }

  @Test
  void addingNewKeyEncryptsWithItAndStillReadsOldCiphertext() {
    String k1 = newKey();
    String oldCiphertext = new VersionedKeyring(k1, "", "", MESSAGES).encrypt("secret");

    VersionedKeyring rotated = new VersionedKeyring(k1, "v2:" + newKey(), "", MESSAGES);

    assertThat(rotated.currentVersion()).isEqualTo("v2");
    assertThat(rotated.decrypt(oldCiphertext)).isEqualTo("secret");
    assertThat(rotated.isCurrent(oldCiphertext)).isFalse();
    String fresh = rotated.encrypt("secret");
    assertThat(fresh).startsWith("v2:");
    assertThat(rotated.isCurrent(fresh)).isTrue();
  }

  @Test
  void explicitCurrentVersionOverridesTheHighestNumber() {
    VersionedKeyring ring = new VersionedKeyring(newKey(), "v2:" + newKey(), "v1", MESSAGES);

    assertThat(ring.currentVersion()).isEqualTo("v1");
    assertThat(ring.encrypt("x")).startsWith("v1:");
  }

  @Test
  void removedOldKeyMakesOldCiphertextUnreadableWithoutLeakingValues() {
    String k1 = newKey();
    String oldCiphertext = new VersionedKeyring(k1, "", "", MESSAGES).encrypt("secret");
    VersionedKeyring onlyNew = new VersionedKeyring("", "v2:" + newKey(), "", MESSAGES);

    assertThatThrownBy(() -> onlyNew.decrypt(oldCiphertext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("복호화 실패")
        .hasMessageContaining("v1")
        .hasMessageNotContaining("secret");
  }

  @Test
  void invalidConfigurationIsRejected() {
    assertThatThrownBy(() -> new VersionedKeyring("", "oops", "", MESSAGES))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new VersionedKeyring(newKey(), "v1:" + newKey(), "", MESSAGES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("중복");
    assertThatThrownBy(() -> new VersionedKeyring(newKey(), "", "v9", MESSAGES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("v9");
    String shortKey = Base64.getEncoder().encodeToString(new byte[8]);
    assertThatThrownBy(() -> new VersionedKeyring("", "v2:" + shortKey, "", MESSAGES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("키 형식 오류");
  }

  @Test
  void noKeysMeansUnconfigured() {
    VersionedKeyring ring = new VersionedKeyring("", "", "", MESSAGES);

    assertThat(ring.isConfigured()).isFalse();
    assertThatThrownBy(() -> ring.encrypt("x")).hasMessage("키 없음");
    assertThatThrownBy(() -> ring.decrypt("v1:AAAA")).hasMessage("키 없음");
  }
}
