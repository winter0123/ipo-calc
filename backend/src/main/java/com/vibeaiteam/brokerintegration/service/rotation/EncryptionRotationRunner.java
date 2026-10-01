package com.vibeaiteam.brokerintegration.service.rotation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code APP_ENCRYPTION_ROTATE_ON_STARTUP=true}일 때만 기동 직후 한 번 저장된 암호문을 현재 키 버전으로 다시 암호화한다.
 *
 * <p>실행 뒤에는 이 값을 끄고, 로그의 {@code failures=0}을 확인한 다음 예전 키를 지운다.
 */
@Component
@ConditionalOnProperty(name = "app.encryption.rotate-on-startup", havingValue = "true")
public class EncryptionRotationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(EncryptionRotationRunner.class);

  private final EncryptionRotationService rotationService;

  /**
   * 생성자.
   *
   * @param rotationService 재암호화 서비스
   */
  public EncryptionRotationRunner(EncryptionRotationService rotationService) {
    this.rotationService = rotationService;
  }

  @Override
  public void run(ApplicationArguments args) {
    EncryptionRotationService.Result result = rotationService.rotateAll();
    if (result.failures() > 0) {
      log.error("재암호화에 실패한 값이 {}건 있다. 예전 키를 지우지 말고 원인을 확인한다.", result.failures());
    }
  }
}
