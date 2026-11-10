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

/** app.ipo-sync.enabled=false일 때 복구 러너와 배치 스케줄러가 생성되지 않음을 확인한다. */
@SpringBootTest(properties = "app.ipo-sync.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = IpoCalcApplication.class)
class PostDeployReconciliationRunnerDisabledTest {

  @Autowired private ApplicationContext context;

  @Test
  void runnerIsAbsentWhenSyncDisabled() {
    assertThat(context.getBeanNamesForType(PostDeployReconciliationRunner.class)).isEmpty();
    assertThat(context.getBeanNamesForType(IpoSyncScheduler.class)).isEmpty();
  }
}
