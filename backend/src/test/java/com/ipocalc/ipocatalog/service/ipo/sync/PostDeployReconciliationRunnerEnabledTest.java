package com.ipocalc.ipocatalog.service.ipo.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipocalc.IpoCalcApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** app.ipo-sync.enabled=true일 때 복구 러너와 배치 스케줄러가 생성됨을 확인한다(미설정 기본값은 true). */
@SpringBootTest(properties = "app.ipo-sync.enabled=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class PostDeployReconciliationRunnerEnabledTest {

  @Autowired private ApplicationContext context;

  @Test
  void runnerIsPresentWhenSyncEnabled() {
    assertThat(context.getBeanNamesForType(PostDeployReconciliationRunner.class)).isNotEmpty();
    assertThat(context.getBeanNamesForType(IpoSyncScheduler.class)).isNotEmpty();
  }
}
