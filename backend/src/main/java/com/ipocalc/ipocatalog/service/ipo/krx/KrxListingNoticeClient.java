package com.ipocalc.ipocatalog.service.ipo.krx;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * 공식 KIND 추가상장 공고(70791.htm)를 읽기 전용으로 조회한다.
 *
 * <p>host/path/프로토콜을 고정하고 redirect를 차단한다. 실제 외부 호출은 운영/테스트에서 mock 가능하도록 {@link RestClient}를 주입받는다.
 */
@Slf4j
public class KrxListingNoticeClient {

  public static final String ALLOWED_HOST = "kind.krx.co.kr";
  public static final String ALLOWED_SCHEME = "https";
  public static final int MAX_RESPONSE_BYTES = 1_000_000;
  public static final int CONNECT_TIMEOUT_MS = 5_000;
  public static final int READ_TIMEOUT_MS = 10_000;
  private static final Pattern PATH_PATTERN =
      Pattern.compile("^/external/\\d{4}/\\d{2}/\\d{2}/\\d{6}/\\d{14}/70791\\.htm$");
  private static final Pattern NEW_PATH_PATTERN =
      Pattern.compile("^/external/\\d{4}/\\d{2}/\\d{2}/\\d{6}/\\d{14}/70760\\.htm$");

  private final RestClient restClient;

  /** RestClient를 직접 주입하는 생성자. */
  public KrxListingNoticeClient(RestClient restClient) {
    this.restClient = restClient;
  }

  /** Builder로 no-redirect 요청 팩토리를 적용해 생성한다. */
  public KrxListingNoticeClient(RestClient.Builder builder) {
    this(builder.requestFactory(noRedirectRequestFactory()).build());
  }

  private static org.springframework.http.client.SimpleClientHttpRequestFactory
      noRedirectRequestFactory() {
    org.springframework.http.client.SimpleClientHttpRequestFactory factory =
        new org.springframework.http.client.SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
              throws java.io.IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
          }
        };
    factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
    factory.setReadTimeout(READ_TIMEOUT_MS);
    return factory;
  }

  /** URL이 공식 KIND 추가상장 공고 형식인지 검증한다. */
  public static void validateNoticeUrl(String url) {
    validateListingUrl(url, PATH_PATTERN);
  }

  /** URL이 공식 KIND 신규상장 공고(70760.htm) 형식인지 검증한다. */
  public static void validateNewListingUrl(String url) {
    validateListingUrl(url, NEW_PATH_PATTERN);
  }

  private static void validateListingUrl(String url, Pattern allowedPath) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("KIND 공고 URL이 비어 있습니다");
    }
    URI uri;
    try {
      uri = new URI(url.trim());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("KIND 공고 URL 형식이 잘못되었습니다", e);
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("KIND 공고 URL에 사용자 정보를 포함할 수 없습니다");
    }
    if (!ALLOWED_SCHEME.equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("KIND 공고 URL은 https만 허용합니다");
    }
    if (!ALLOWED_HOST.equalsIgnoreCase(uri.getHost())) {
      throw new IllegalArgumentException("KIND 공고 URL host가 허용되지 않습니다");
    }
    if (uri.getPort() != -1 && uri.getPort() != 443) {
      throw new IllegalArgumentException("KIND 공고 URL 포트가 허용되지 않습니다");
    }
    String path = uri.getPath();
    if (path == null || !allowedPath.matcher(path).matches()) {
      throw new IllegalArgumentException("KIND 공고 URL path가 허용되지 않습니다");
    }
    if (uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException("KIND 공고 URL에 query/fragment를 포함할 수 없습니다");
    }
    // 날짜 부분 유효성
    String[] parts = path.split("/");
    try {
      int year = Integer.parseInt(parts[2]);
      int month = Integer.parseInt(parts[3]);
      int day = Integer.parseInt(parts[4]);
      java.time.LocalDate.of(year, month, day);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("KIND 공고 URL 날짜가 유효하지 않습니다", e);
    }
  }

  /** URL 허용 여부를 반환한다. */
  public static boolean isAllowedNoticeUrl(String url) {
    try {
      validateNoticeUrl(url);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** 추가상장 원문을 읽는다. */
  public String fetch(String noticeUrl) {
    validateNoticeUrl(noticeUrl);
    return fetchValidated(noticeUrl);
  }

  /** 신규상장 원문을 읽는다. */
  public String fetchNewListing(String noticeUrl) {
    validateNewListingUrl(noticeUrl);
    return fetchValidated(noticeUrl);
  }

  /** 공식 공고 HTML을 읽는다. redirect/오류/크기 초과 시 예외를 던진다. */
  private String fetchValidated(String noticeUrl) {
    String url = noticeUrl.trim();
    try {
      return restClient
          .get()
          .uri(URI.create(url))
          .exchange(
              (request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                  throw new IllegalArgumentException(
                      "KIND 공고 조회 실패: HTTP " + response.getStatusCode().value());
                }
                java.io.InputStream body = response.getBody();
                if (body == null) {
                  throw new IllegalArgumentException("KIND 공고 응답이 비어 있습니다");
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = body.read(buf)) != -1) {
                  total += n;
                  if (total > MAX_RESPONSE_BYTES) {
                    throw new IllegalArgumentException("KIND 공고 응답이 크기 한도를 초과했습니다");
                  }
                  out.write(buf, 0, n);
                }
                String text = out.toString(java.nio.charset.StandardCharsets.UTF_8);
                if (text.isBlank()) {
                  throw new IllegalArgumentException("KIND 공고 응답이 비어 있습니다");
                }
                return text;
              });
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("KIND 공고 조회 실패: " + e.getClass().getSimpleName());
    }
  }
}
