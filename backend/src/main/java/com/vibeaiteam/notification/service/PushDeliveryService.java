package com.vibeaiteam.notification.service;

import com.vibeaiteam.accountauthentication.domain.model.RecordStatus;
import com.vibeaiteam.notification.domain.model.PushSubscription;
import com.vibeaiteam.notification.repository.PushSubscriptionRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 한 계정의 모든 기기로 푸시를 보내고, 만료된 구독(404/410)은 소프트 삭제한다. */
@Service
public class PushDeliveryService {

  private final PushSubscriptionRepository subscriptions;
  private final PushSender sender;
  // 슬라이스 테스트(@DataJpaTest)에도 빈이 없어도 뜨도록 주입받지 않고 직접 만든다
  private static final ObjectMapper MAPPER = JsonMapper.builder().build();


  /** 의존성을 주입받는다. */
  public PushDeliveryService(PushSubscriptionRepository subscriptions, PushSender sender) {
    this.subscriptions = subscriptions;
    this.sender = sender;
  }

  /**
   * 계정의 활성 구독 전부에 알림을 보낸다.
   *
   * @param url 알림을 누르면 열 앱 내부 경로(예: /ipos/3)
   * @return 발송에 성공한 기기 수
   */
  public int deliver(Long accountId, String title, String body, String url, String tag) {
    Map<String, String> message = new LinkedHashMap<>();
    message.put("title", title);
    message.put("body", body);
    message.put("url", url);
    message.put("tag", tag);
    byte[] payload = MAPPER.writeValueAsBytes(message);
    int ok = 0;
    for (PushSubscription sub :
        subscriptions.findByAccountIdAndStatus(accountId, RecordStatus.ACTIVE)) {
      PushSender.Result result = sender.send(sub, payload);
      if (result == PushSender.Result.OK) {
        sub.setLastSuccessAt(LocalDateTime.now());
        subscriptions.save(sub);
        ok++;
      } else if (result == PushSender.Result.GONE) {
        sub.cancel();
        subscriptions.save(sub);
      }
    }
    return ok;
  }
}
