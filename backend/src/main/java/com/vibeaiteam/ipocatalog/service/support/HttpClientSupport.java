package com.vibeaiteam.ipocatalog.service.support;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** 외부 API 클라이언트 공용 HTTP 설정. */
public final class HttpClientSupport {

  private HttpClientSupport() {}

  /** 연결·읽기 타임아웃(ms)을 설정한 요청 팩토리를 만든다. */
  public static SimpleClientHttpRequestFactory requestFactory(
      int connectTimeoutMs, int readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    return factory;
  }

  /** 연결 5초·읽기 15초 기본 타임아웃의 요청 팩토리. */
  public static SimpleClientHttpRequestFactory requestFactory() {
    return requestFactory(5_000, 15_000);
  }

  /** 호출 사이 최소 간격(ms)을 보장하는 스레드 안전 조절기. 같은 키·호스트를 쓰는 클라이언트끼리 한 인스턴스를 공유한다. */
  public static final class RequestThrottle {

    private final long minIntervalMs;
    private long lastRequestTimeMs = 0;

    /** 최소 간격(ms)을 지정한다. 0이면 대기하지 않는다. */
    public RequestThrottle(long minIntervalMs) {
      this.minIntervalMs = minIntervalMs;
    }

    /** 직전 호출로부터 최소 간격이 지날 때까지 대기한 뒤 호출 시각을 기록한다. */
    public synchronized void acquire() {
      long wait = minIntervalMs - (System.currentTimeMillis() - lastRequestTimeMs);
      if (wait > 0) {
        try {
          Thread.sleep(wait);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("요청 간격 대기 중 인터럽트 발생", e);
        }
      }
      lastRequestTimeMs = System.currentTimeMillis();
    }
  }
}
