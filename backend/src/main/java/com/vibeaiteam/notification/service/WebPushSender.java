package com.vibeaiteam.notification.service;

import com.vibeaiteam.notification.config.WebPushProperties;
import com.vibeaiteam.notification.domain.model.PushSubscription;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Security;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

/** web-push 라이브러리(VAPID 서명 + aes128gcm 암호화)로 실제 발송한다. 로그에 endpoint·키를 남기지 않는다. */
@Component
@Slf4j
public class WebPushSender implements PushSender {

  private static final Set<String> RESTRICTED = Set.of("content-length", "host", "connection");

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final WebPushProperties properties;
  private volatile PushService pushService;

  /** 설정을 주입받는다. 서비스는 처음 쓸 때 만든다(키가 없는 환경에서도 기동돼야 하므로). */
  public WebPushSender(WebPushProperties properties) {
    this.properties = properties;
  }

  @Override
  public Result send(PushSubscription subscription, byte[] payload) {
    try {
      // 라이브러리는 암호화·VAPID 서명(요청 만들기)만 맡기고, 전송은 타임아웃을 건 JDK HttpClient로 한다
      HttpPost post =
          service()
              .preparePost(
                  new Notification(
                      subscription.getEndpoint(),
                      subscription.getP256dh(),
                      subscription.getAuth(),
                      payload),
                  Encoding.AES128GCM);
      HttpRequest.Builder request =
          HttpRequest.newBuilder(post.getURI())
              .timeout(Duration.ofSeconds(10))
              .POST(
                  HttpRequest.BodyPublishers.ofByteArray(
                      EntityUtils.toByteArray(post.getEntity())));
      for (Header header : post.getAllHeaders()) {
        // Content-Length 등은 JDK가 정한다
        if (!RESTRICTED.contains(header.getName().toLowerCase(Locale.ROOT))) {
          request.header(header.getName(), header.getValue());
        }
      }
      HttpResponse<String> response =
          http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status >= 400) {
        log.debug("푸시 서비스 응답 본문: {}", response.body());
      }
      if (status >= 200 && status < 300) {
        return Result.OK;
      }
      if (status == 404 || status == 410) {
        log.info("푸시 구독 만료(status={}): 구독을 정리합니다", status);
        return Result.GONE;
      }
      log.warn("푸시 발송 거절: status={}", status);
      return Result.FAILED;
    } catch (Exception e) {
      // 예외 메시지에 endpoint가 들어갈 수 있어 클래스명만 남긴다
      log.warn("푸시 발송 실패: {}", e.getClass().getSimpleName());
      return Result.FAILED;
    }
  }

  private PushService service() throws java.security.GeneralSecurityException {
    PushService local = pushService;
    if (local == null) {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(new BouncyCastleProvider());
      }
      local =
          new PushService(
              properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
      pushService = local;
    }
    return local;
  }
}
