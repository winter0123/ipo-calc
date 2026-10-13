package com.vibeaiteam.ipocatalog.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.vibeaiteam.ipocatalog.service.support.HttpClientSupport.RequestThrottle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpClientSupportTest {

  @Test
  void acquire_keepsMinimumIntervalAcrossThreads() throws Exception {
    RequestThrottle throttle = new RequestThrottle(50);
    List<Long> times = Collections.synchronizedList(new ArrayList<>());
    Runnable task =
        () -> {
          throttle.acquire();
          times.add(System.nanoTime());
        };
    Thread a = new Thread(task);
    Thread b = new Thread(task);
    a.start();
    b.start();
    a.join();
    b.join();

    long gapMs = Math.abs(times.get(1) - times.get(0)) / 1_000_000;
    assertThat(gapMs).isGreaterThanOrEqualTo(45);
  }
}
